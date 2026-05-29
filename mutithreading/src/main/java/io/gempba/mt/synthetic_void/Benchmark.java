package io.gempba.mt.synthetic_void;

import io.gempba.*;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.value.Score;
import io.gempba.utils.BenchmarkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded counterpart of {@link io.gempba.seq.synthetic_void.Benchmark}
 * — same recursion shape, same per-node cost model, but children are dispatched
 * to the GemPBA thread pool via {@code tryLocalSubmit}/{@code forward}.
 *
 * <p>Result delivery is side-effecting: each leaf bumps an {@link AtomicLong}
 * and pushes the running count through {@code tryUpdateResult} as a {@link Score}.
 * For the non-void counterpart that returns the leaf sum directly from
 * {@code explore}, see {@link io.gempba.mt.synthetic_leafsum.Benchmark}.
 */
public class Benchmark {

    private final NodeManager mNodeManager;
    private final LoadBalancer mLoadBalancer;
    private final AtomicLong mLeafCount = new AtomicLong(0);

    public Benchmark(NodeManager nodeManager, LoadBalancer loadBalancer) {
        this.mNodeManager = nodeManager;
        this.mLoadBalancer = loadBalancer;
    }

    /**
     * Recursive traversal — mirrors {@code mt_benchmark::explore} in C++.
     */
    public void explore(long threadId, int depth, int maxDepth, List<Double> arg, Node parent) {
        if (depth == maxDepth) {
            BenchmarkUtils.busyForMicroseconds(BenchmarkUtils.LEAF_COST_US);
            // C++ long is 32-bit on Windows; cast to int to produce an I32 score
            // matching score::make(m_leaf_count) in the C++ benchmark.
            int leafCount = (int) mLeafCount.incrementAndGet();
            mNodeManager.tryUpdateResult(leafCount, Score.make(leafCount));
            return;
        }

        // Per-node work
        int cost = BenchmarkUtils.INTERNAL_COST_US;
        if (BenchmarkUtils.RANDOMIZE_COST) {
            cost = Math.max(1, BenchmarkUtils.INTERNAL_COST_US + BenchmarkUtils.jitter());
        }

        BenchmarkUtils.simulateArgumentBuild(arg, depth);
        BenchmarkUtils.busyForMicroseconds(cost);

        Node vParent = (parent == null) ? GemPBA.createDummyNode(mLoadBalancer) : parent;

        // Create branch arguments
        List<Double> leftArg = new ArrayList<>(arg);
        List<Double> rightArg = new ArrayList<>(arg);

        leftArg.set(depth % BenchmarkUtils.ARG_SIZE, leftArg.get(depth % BenchmarkUtils.ARG_SIZE) + 0.001);
        rightArg.set(depth % BenchmarkUtils.ARG_SIZE, rightArg.get(depth % BenchmarkUtils.ARG_SIZE) - 0.001);

        Node leftNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent,
                (lt, node) -> explore(lt, depth + 1, maxDepth, leftArg, node));

        Node rightNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent,
                (lt, node) -> explore(lt, depth + 1, maxDepth, rightArg, node));

        mNodeManager.tryLocalSubmit(leftNode);
        mNodeManager.forward(rightNode);
    }
}

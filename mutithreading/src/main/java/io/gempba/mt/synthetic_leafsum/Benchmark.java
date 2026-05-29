package io.gempba.mt.synthetic_leafsum;

import io.gempba.GemPBA;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.task.Deserializer;
import io.gempba.task.Serializer;
import io.gempba.utils.BenchmarkUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-threaded counterpart of {@link io.gempba.seq.synthetic_void.Benchmark}
 * — same recursion shape, same per-node cost model, but children are executed
 * in parallel via the GemPBA thread pool and each call <em>returns</em> its
 * leaf sum instead of side-effecting through {@code tryUpdateResult}.
 *
 * <p>Mirrors the C++ pattern in
 * {@code gempba-examples/multithreading/include/graph_opt_enc_semi_non_void.hpp}:
 * each recursive call submits the left child to the pool with
 * {@code tryLocalSubmit}, runs the right child inline with {@code forward},
 * then retrieves both children's typed return values via {@code Node#getResult()}
 * and returns their sum.
 */
public class Benchmark {

    private static final Serializer<Long> LONG_SER = v -> ByteBuffer.allocate(Long.BYTES).putLong(v).array();
    private static final Deserializer<Long> LONG_DESER = b -> ByteBuffer.wrap(b).getLong();

    private final NodeManager mNodeManager;
    private final LoadBalancer mLoadBalancer;

    public Benchmark(NodeManager nodeManager, LoadBalancer loadBalancer) {
        this.mNodeManager = nodeManager;
        this.mLoadBalancer = loadBalancer;
    }

    public long explore(long threadId, int depth, int maxDepth, List<Double> arg, Node parent) {
        if (depth == maxDepth) {
            BenchmarkUtils.busyForMicroseconds(BenchmarkUtils.LEAF_COST_US);
            return 1L;
        }

        int cost = BenchmarkUtils.INTERNAL_COST_US;
        if (BenchmarkUtils.RANDOMIZE_COST) {
            cost = Math.max(1, BenchmarkUtils.INTERNAL_COST_US + BenchmarkUtils.jitter());
        }

        BenchmarkUtils.simulateArgumentBuild(arg, depth);
        BenchmarkUtils.busyForMicroseconds(cost);

        Node vParent = (parent == null) ? GemPBA.createDummyNode(mLoadBalancer) : parent;

        List<Double> leftArg = new ArrayList<>(arg);
        List<Double> rightArg = new ArrayList<>(arg);
        leftArg.set(depth % BenchmarkUtils.ARG_SIZE, leftArg.get(depth % BenchmarkUtils.ARG_SIZE) + 0.001);
        rightArg.set(depth % BenchmarkUtils.ARG_SIZE, rightArg.get(depth % BenchmarkUtils.ARG_SIZE) - 0.001);

        Node leftNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent,
                (lt, node) -> explore(lt, depth + 1, maxDepth, leftArg, node),
                LONG_SER);

        Node rightNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent,
                (lt, node) -> explore(lt, depth + 1, maxDepth, rightArg, node),
                LONG_SER);

        mNodeManager.tryLocalSubmit(leftNode);
        mNodeManager.forward(rightNode);

        long leftCount = LONG_DESER.deserialize(leftNode.getResult());
        long rightCount = LONG_DESER.deserialize(rightNode.getResult());
        return leftCount + rightCount;
    }
}

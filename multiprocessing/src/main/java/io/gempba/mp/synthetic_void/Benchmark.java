package io.gempba.mp.synthetic_void;

import io.gempba.*;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.task.Deserializer;
import io.gempba.task.NodeTask;
import io.gempba.task.Serializer;
import io.gempba.value.Score;
import io.gempba.utils.BenchmarkUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-process counterpart of {@link io.gempba.mt.synthetic_void.Benchmark}.
 *
 * <p>Mirrors the recursive binary traversal but creates each child node
 * with the MP {@code createExplicitNode} signature so the load balancer
 * can dispatch work across MPI ranks.  Every child node carries its
 * arguments as a serialised {@link Args} payload — the C++ side hands
 * those bytes off via the IPC scheduler when a remote rank picks up the
 * task.
 */
public class Benchmark {

    /**
     * Numeric ID the worker registers this runnable under.
     */
    public static final int RUNNABLE_ID = 1;

    /**
     * Bundled args — {@code (depth, maxDepth, arg)} — moved across ranks as bytes.
     */
    public record Args(int depth, int maxDepth, List<Double> arg) {
    }

    public static final Serializer<Args> SERIALIZER = Benchmark::serialize;
    public static final Deserializer<Args> DESERIALIZER = Benchmark::deserialize;

    private final NodeManager mNodeManager;
    private final LoadBalancer mLoadBalancer;
    private final AtomicLong mLeafCount = new AtomicLong(0);

    public Benchmark(NodeManager nodeManager, LoadBalancer loadBalancer) {
        this.mNodeManager = nodeManager;
        this.mLoadBalancer = loadBalancer;
    }

    /**
     * Recursive traversal — mirrors {@code mp_benchmark::explore} in C++.
     */
    public void explore(long threadId, int depth, int maxDepth, List<Double> arg, Node parent) {
        if (depth == maxDepth) {
            BenchmarkUtils.busyForMicroseconds(BenchmarkUtils.LEAF_COST_US);
            int leafCount = (int) mLeafCount.incrementAndGet();
            mNodeManager.tryUpdateResult(leafCount, Score.make(leafCount));
            return;
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
        leftArg.set(depth % BenchmarkUtils.ARG_SIZE,
                leftArg.get(depth % BenchmarkUtils.ARG_SIZE) + 0.001);
        rightArg.set(depth % BenchmarkUtils.ARG_SIZE,
                rightArg.get(depth % BenchmarkUtils.ARG_SIZE) - 0.001);

        // Same task closure for both children; the framework dispatches the
        // serialised Args (locally OR remotely via tryRemoteSubmit).
        NodeTask<Args, Void> task = (lt, a, node) -> {
            explore(lt, a.depth(), a.maxDepth(), new ArrayList<>(a.arg()), node);
            return null;
        };

        Node leftNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent, task,
                new Args(depth + 1, maxDepth, leftArg),
                SERIALIZER, DESERIALIZER);
        Node rightNode = GemPBA.createExplicitNode(
                mLoadBalancer, vParent, task,
                new Args(depth + 1, maxDepth, rightArg),
                SERIALIZER, DESERIALIZER);

        mNodeManager.tryRemoteSubmit(leftNode, RUNNABLE_ID);
        mNodeManager.forward(rightNode);
    }

    // ─── Wire format ────────────────────────────────────────────────────────
    // Layout: int depth | int maxDepth | int n | n * double arg.

    private static byte[] serialize(Args a) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(12 + 8 * a.arg().size());
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(a.depth());
            dos.writeInt(a.maxDepth());
            dos.writeInt(a.arg().size());
            for (double d : a.arg()) dos.writeDouble(d);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Args deserialize(byte[] bytes) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int depth = dis.readInt();
            int maxDepth = dis.readInt();
            int n = dis.readInt();
            List<Double> arg = new ArrayList<>(n);
            for (int i = 0; i < n; i++) arg.add(dis.readDouble());
            return new Args(depth, maxDepth, arg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

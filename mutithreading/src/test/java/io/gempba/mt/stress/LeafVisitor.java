package io.gempba.mt.stress;

import io.gempba.GemPBA;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.task.Deserializer;
import io.gempba.task.Serializer;
import io.gempba.task.VoidNodeTask;
import io.gempba.value.BalancingPolicy;
import io.gempba.value.Goal;
import io.gempba.value.Score;
import io.gempba.value.ScoreType;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Walks a full binary tree on one process's threads and checks two answers that don't depend on how the work was
 * split: the leaf count, 2^depth, and the best leaf, 2^depth - 1. Exits non-zero when either is wrong.
 *
 * <p>Usage: {@code LeafVisitor <threads> <depth>}
 */
public final class LeafVisitor {

    record Task(int depth, int index) {
    }

    private static final Serializer<Task> SERIALIZER = task -> ByteBuffer.allocate(2 * Integer.BYTES)
            .putInt(task.depth()).putInt(task.index()).array();
    private static final Deserializer<Task> DESERIALIZER = bytes -> {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new Task(buffer.getInt(), buffer.getInt());
    };

    private final LoadBalancer mLoadBalancer;
    private final NodeManager mNodeManager;
    private final int mDepth;
    private final AtomicLong mLeaves = new AtomicLong();

    private LeafVisitor(LoadBalancer loadBalancer, NodeManager nodeManager, int depth) {
        mLoadBalancer = loadBalancer;
        mNodeManager = nodeManager;
        mDepth = depth;
    }

    private void visit(int depth, int index, Node parent) {
        if (depth == mDepth) {
            mLeaves.incrementAndGet();
            mNodeManager.tryUpdateResult(index, Score.make(index));
            return;
        }
        Node vParent = parent == null ? GemPBA.createDummyNode(mLoadBalancer) : parent;
        Node left = GemPBA.createExplicitNode(mLoadBalancer, vParent, (tid, node) -> visit(depth + 1, 2 * index, node));
        Node right = GemPBA.createExplicitNode(mLoadBalancer, vParent, (tid, node) -> visit(depth + 1, 2 * index + 1, node));
        mNodeManager.tryLocalSubmit(left);
        mNodeManager.forward(right);
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 2) {
            System.err.println("usage: LeafVisitor <threads> <depth>");
            System.exit(2);
        }
        int threads = Integer.parseInt(args[0]);
        int depth = Integer.parseInt(args[1]);

        LoadBalancer loadBalancer = GemPBA.createLoadBalancer(BalancingPolicy.QUASI_HORIZONTAL);
        NodeManager nodeManager = GemPBA.createNodeManager(loadBalancer);
        nodeManager.setGoal(Goal.MAXIMISE, ScoreType.I32);
        nodeManager.setScore(Score.make(-1));
        nodeManager.setThreadPoolSize(threads);

        LeafVisitor visitor = new LeafVisitor(loadBalancer, nodeManager, depth);
        Node seed = GemPBA.createSeedNode(loadBalancer,
                (VoidNodeTask<Task>) (tid, task, node) -> visitor.visit(task.depth(), task.index(), node),
                new Task(0, 0), SERIALIZER, DESERIALIZER);
        if (!nodeManager.tryLocalSubmit(seed)) {
            System.err.println("unable to submit the seed");
            System.exit(1);
        }
        nodeManager.waitForCompletion();

        long leaves = visitor.mLeaves.get();
        int bestLeaf = nodeManager.<Integer>getResult().orElse(-1);
        long expectedLeaves = 1L << depth;
        int expectedBestLeaf = (int) (expectedLeaves - 1);
        System.out.printf("%d threads: %d leaves (expected %d), best leaf %d (expected %d)%n",
                threads, leaves, expectedLeaves, bestLeaf, expectedBestLeaf);

        int shutdownCode = GemPBA.shutdown();
        if (leaves != expectedLeaves || bestLeaf != expectedBestLeaf) {
            System.exit(1);
        }
        System.exit(shutdownCode);
    }
}

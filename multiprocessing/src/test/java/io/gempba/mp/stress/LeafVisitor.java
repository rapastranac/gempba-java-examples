package io.gempba.mp.stress;

import io.gempba.GemPBA;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.mpi.Mpi;
import io.gempba.mpi.SchedulerTopology;
import io.gempba.scheduler.Scheduler;
import io.gempba.scheduler.SerialRunnable;
import io.gempba.task.Deserializer;
import io.gempba.task.Serializer;
import io.gempba.task.VoidNodeTask;
import io.gempba.value.BalancingPolicy;
import io.gempba.value.Goal;
import io.gempba.value.Score;
import io.gempba.value.ScoreType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Walks a full binary tree across MPI ranks on the semi-centralized scheduler. Every rank prints how many leaves it
 * visited, which must sum to 2^depth; the Java binding has no MPI reduce, so the caller adds them up. Rank 0 checks
 * the best leaf, 2^depth - 1, and exits non-zero when it is wrong.
 *
 * <p>Usage: {@code mpiexec -n <ranks> java ... LeafVisitor <threads per rank> <depth>}
 */
public final class LeafVisitor {

    record Task(int depth, int index) {
    }

    private static final int RUNNABLE_ID = 0;

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
    private final VoidNodeTask<Task> mVisit = (tid, task, node) -> visit(task.depth(), task.index(), node);

    private LeafVisitor(LoadBalancer loadBalancer, NodeManager nodeManager, int depth) {
        mLoadBalancer = loadBalancer;
        mNodeManager = nodeManager;
        mDepth = depth;
    }

    private void visit(int depth, int index, Node parent) {
        if (depth == mDepth) {
            mLeaves.incrementAndGet();
            mNodeManager.tryUpdateResult(ByteBuffer.allocate(Integer.BYTES).putInt(index).array(), Score.make(index));
            return;
        }
        Node vParent = parent == null ? GemPBA.createDummyNode(mLoadBalancer) : parent;
        Node left = GemPBA.createExplicitNode(mLoadBalancer, vParent, mVisit, new Task(depth + 1, 2 * index), SERIALIZER, DESERIALIZER);
        Node right = GemPBA.createExplicitNode(mLoadBalancer, vParent, mVisit, new Task(depth + 1, 2 * index + 1), SERIALIZER, DESERIALIZER);
        mNodeManager.tryRemoteSubmit(left, RUNNABLE_ID);
        mNodeManager.forward(right);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: mpiexec -n <ranks> java ... LeafVisitor <threads per rank> <depth>");
            System.exit(2);
        }
        int threads = Integer.parseInt(args[0]);
        int depth = Integer.parseInt(args[1]);

        Scheduler scheduler = Mpi.createScheduler(SchedulerTopology.SEMI_CENTRALIZED);
        LoadBalancer loadBalancer = GemPBA.createLoadBalancer(BalancingPolicy.QUASI_HORIZONTAL, scheduler);
        NodeManager nodeManager = GemPBA.createNodeManager(loadBalancer, scheduler);
        scheduler.setGoal(Goal.MAXIMISE, ScoreType.I32);
        nodeManager.setGoal(Goal.MAXIMISE, ScoreType.I32);
        nodeManager.setScore(Score.make(-1));
        if (!scheduler.isRoot()) {
            nodeManager.setThreadPoolSize(threads);
        }

        LeafVisitor visitor = new LeafVisitor(loadBalancer, nodeManager, depth);
        SerialRunnable runnable = GemPBA.runnableReturnNone(RUNNABLE_ID, (tid, bytes) -> {
            Task task = DESERIALIZER.deserialize(bytes);
            visitor.visit(task.depth(), task.index(), null);
            return null;
        });

        byte[] seed = scheduler.isRoot() ? SERIALIZER.serialize(new Task(0, 0)) : null;
        scheduler.run(nodeManager, Map.of(RUNNABLE_ID, runnable), seed, RUNNABLE_ID);
        runnable.close();

        printLine("rank %d: %d leaves".formatted(scheduler.rankMe(), visitor.mLeaves.get()));

        int exitCode = 0;
        if (scheduler.isRoot()) {
            byte[] result = scheduler.getResult();
            int bestLeaf = result != null && result.length == Integer.BYTES ? ByteBuffer.wrap(result).getInt() : -1;
            int expectedBestLeaf = (int) ((1L << depth) - 1);
            printLine("%d ranks, %d threads per rank: best leaf %d (expected %d)".formatted(
                    scheduler.worldSize(), threads, bestLeaf, expectedBestLeaf));
            if (bestLeaf != expectedBestLeaf) {
                exitCode = 1;
            }
        }

        int shutdownCode = GemPBA.shutdown();
        System.exit(exitCode != 0 ? exitCode : shutdownCode);
    }

    /**
     * Writes the line in a single write. All ranks share the launcher's output, and printf writes a line in pieces,
     * so the ranks' lines would interleave.
     */
    private static void printLine(String line) {
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        System.out.write(bytes, 0, bytes.length);
        System.out.flush();
    }
}

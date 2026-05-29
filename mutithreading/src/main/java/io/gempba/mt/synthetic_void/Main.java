package io.gempba.mt.synthetic_void;

import io.gempba.*;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.task.VoidNodeTask;
import io.gempba.value.BalancingPolicy;
import io.gempba.value.Goal;
import io.gempba.value.Score;
import io.gempba.value.ScoreType;

import java.util.ArrayList;
import java.util.List;

public class Main {

    // --- Configuration ---
    private static final int ARG_SIZE = 256;
    private static final int CPUS_PER_TASK = 24;
    private static final int MAX_DEPTH = 24;
    // --------------------

    static void main() throws InterruptedException {

        LoadBalancer lb = GemPBA.createLoadBalancer(BalancingPolicy.QUASI_HORIZONTAL);
        NodeManager nm = GemPBA.createNodeManager(lb);

        nm.setGoal(Goal.MAXIMISE, ScoreType.I32);
        nm.setThreadPoolSize(CPUS_PER_TASK);
        nm.setScore(Score.make(0)); // arbitrary initial score — mirrors set_score(score::make(0)) in C++

        Benchmark benchmark = new Benchmark(nm, lb);

        // std::iota(v_initial_arg.begin(), v_initial_arg.end(), 0.0) → 0.0, 1.0, …, 255.0
        List<Double> initialArg = new ArrayList<>(ARG_SIZE);
        for (int i = 0; i < ARG_SIZE; i++) {
            initialArg.add((double) i);
        }

        System.out.printf("Running synthetic-tree benchmark (MT):%n");
        System.out.printf("  Depth = %d (leaves = %d)%n", MAX_DEPTH, 1 << MAX_DEPTH);

        Node seed = GemPBA.createSeedNode(lb,
                (VoidNodeTask<Integer>) (tid, depth, node) ->
                        benchmark.explore(tid, depth, MAX_DEPTH, initialArg, node),
                0,
                v -> new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) (int) v},
                b -> ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF));

        double startTime = NodeManager.getWallTime();

        if (!nm.tryLocalSubmit(seed)) {
            throw new RuntimeException("unable to submit seed node");
        }
        nm.waitForCompletion();

        double endTime = NodeManager.getWallTime();

        Score score = nm.getScore();
        double elapsedTime = endTime - startTime;
        double globalIdleTime = nm.getIdleTime();
        long threadRequestCount = nm.getThreadRequestCount();

        System.out.printf("Score: %s%n", score);
        System.out.printf("global pool idle time: %.6f seconds%n", globalIdleTime);
        System.out.printf("elapsed time: %.6f seconds%n", elapsedTime);
        System.out.printf("thread requests: %d%n", threadRequestCount);

        nm.<Integer>getResult().ifPresent(r -> System.out.printf("Result: %d%n", r));

        GemPBA.shutdown();
    }
}

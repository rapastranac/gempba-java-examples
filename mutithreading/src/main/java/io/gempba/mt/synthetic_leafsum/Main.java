package io.gempba.mt.synthetic_leafsum;

import io.gempba.GemPBA;
import io.gempba.core.LoadBalancer;
import io.gempba.core.Node;
import io.gempba.core.NodeManager;
import io.gempba.value.BalancingPolicy;

import java.util.ArrayList;
import java.util.List;

public class Main {

    // --- Configuration ---
    private static final int ARG_SIZE = 256;
    private static final int CPUS_PER_TASK = 24;
    private static final int MAX_DEPTH = 24;
    // --------------------

    static void main() {

        LoadBalancer lb = GemPBA.createLoadBalancer(BalancingPolicy.QUASI_HORIZONTAL);
        NodeManager nm = GemPBA.createNodeManager(lb);

        nm.setThreadPoolSize(CPUS_PER_TASK);

        Benchmark benchmark = new Benchmark(nm, lb);

        // std::iota(v_initial_arg.begin(), v_initial_arg.end(), 0.0) → 0.0, 1.0, …, 255.0
        List<Double> initialArg = new ArrayList<>(ARG_SIZE);
        for (int i = 0; i < ARG_SIZE; i++) {
            initialArg.add((double) i);
        }

        System.out.printf("Running synthetic-tree benchmark (MT, leaf-sum):%n");
        System.out.printf("  Depth = %d (leaves = %d)%n", MAX_DEPTH, 1 << MAX_DEPTH);

        // Mirrors graph_opt_enc_semi_non_void.hpp: call the recursion directly
        // from the calling thread with a dummy parent — the framework parallelises
        // children via tryLocalSubmit/forward inside `explore`.
        Node dummy = GemPBA.createDummyNode(lb);

        double startTime = NodeManager.getWallTime();
        long leafSum = benchmark.explore(Thread.currentThread().threadId(), 0, MAX_DEPTH, initialArg, dummy);
        double endTime = NodeManager.getWallTime();

        System.out.printf("Leaf sum: %d%n", leafSum);
        System.out.printf("global pool idle time: %.6f seconds%n", nm.getIdleTime());
        System.out.printf("elapsed time: %.6f seconds%n", endTime - startTime);
        System.out.printf("thread requests: %d%n", nm.getThreadRequestCount());

        GemPBA.shutdown();
    }
}

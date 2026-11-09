package io.gempba.mp.synthetic_void;

import io.gempba.*;
import io.gempba.core.LoadBalancer;
import io.gempba.core.NodeManager;
import io.gempba.scheduler.Scheduler;
import io.gempba.scheduler.SchedulerTopology;
import io.gempba.scheduler.SerialRunnable;
import io.gempba.value.BalancingPolicy;
import io.gempba.value.Goal;
import io.gempba.value.Score;
import io.gempba.value.ScoreType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    // --- Configuration ---
    private static final int ARG_SIZE = 256;
    private static final int CPUS_PER_TASK = 4;
    private static final int MAX_DEPTH = 24;
    // --------------------

    static void main() {

        Scheduler sched = GemPBA.createScheduler(SchedulerTopology.SEMI_CENTRALIZED);
        sched.setGoal(Goal.MAXIMISE, ScoreType.I32);

        int rank = sched.rankMe();

        sched.barrier(); // synchronise before starting the search

        if (rank == 0) {
            // ── Center process ────────────────────────────────────────────────
            System.out.println("USING SEMI-CENTRALIZED STRATEGY");
            System.out.printf("Running synthetic-tree benchmark (MP):%n");
            System.out.printf("  Depth = %d (leaves = %d)%n", MAX_DEPTH, 1 << MAX_DEPTH);

            List<Double> initialArg = new ArrayList<>(ARG_SIZE);
            for (int i = 0; i < ARG_SIZE; i++) {
                initialArg.add((double) i);
            }

            byte[] seedBytes = Benchmark.SERIALIZER.serialize(new Benchmark.Args(0, MAX_DEPTH, initialArg));

            sched.center().run(seedBytes, Benchmark.RUNNABLE_ID);

        } else {
            // ── Worker process ────────────────────────────────────────────────
            Scheduler.Worker workerView = sched.worker();
            LoadBalancer lb = GemPBA.createLoadBalancer(BalancingPolicy.QUASI_HORIZONTAL, workerView);
            NodeManager nm = GemPBA.createNodeManager(lb, workerView);

            nm.setGoal(Goal.MAXIMISE, ScoreType.I32);
            nm.setScore(Score.make(0));
            nm.setThreadPoolSize(CPUS_PER_TASK);

            Benchmark benchmark = new Benchmark(nm, lb);

            SerialRunnable runnable = GemPBA.runnableReturnNone(
                    Benchmark.RUNNABLE_ID,
                    (tid, bytes) -> {
                        Benchmark.Args a = Benchmark.DESERIALIZER.deserialize(bytes);
                        benchmark.explore(tid, a.depth(), a.maxDepth(), new ArrayList<>(a.arg()), null);
                        return null;
                    });

            workerView.run(nm, Map.of(Benchmark.RUNNABLE_ID, runnable));
            runnable.close();
        }

        sched.barrier(); // wait for all processes to finish
        sched.synchronizeStats();

        if (rank == 0) {
            sched.getStats().forEach(System.out::println);
        }

        GemPBA.shutdown();
    }
}

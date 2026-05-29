package io.gempba.seq.synthetic_void;

import io.gempba.utils.BenchmarkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential, single-threaded reference implementation of the synthetic-tree
 * benchmark in {@code gempba-examples/multithreading/src/benchmark.cpp} and
 * {@code gempba-examples/multiprocessing/src/benchmark.cpp}.
 *
 * <p>Same recursion shape and per-node cost model as the GemPBA variants
 * (busy-wait, sin perturbation, +/-0.001 branch args), but with a non-void
 * return: every leaf returns {@code 1}, every internal node returns the sum
 * of its children's returns. The driver receives the total leaf count as
 * {@code explore}'s return value, not via a side-effecting {@code tryUpdateResult}.
 *
 * <p>Establishes the deterministic oracle for future GemPBA "non-void"
 * variants — a parallel run on the same {@code (maxDepth, ARG_SIZE)} must
 * return the same leaf sum.
 */
public class Benchmark {

    public long explore(int depth, int maxDepth, List<Double> arg) {
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

        List<Double> leftArg = new ArrayList<>(arg);
        List<Double> rightArg = new ArrayList<>(arg);

        leftArg.set(depth % BenchmarkUtils.ARG_SIZE, leftArg.get(depth % BenchmarkUtils.ARG_SIZE) + 0.001);
        rightArg.set(depth % BenchmarkUtils.ARG_SIZE, rightArg.get(depth % BenchmarkUtils.ARG_SIZE) - 0.001);

        long leftCount = explore(depth + 1, maxDepth, leftArg);
        long rightCount = explore(depth + 1, maxDepth, rightArg);
        return leftCount + rightCount;
    }
}

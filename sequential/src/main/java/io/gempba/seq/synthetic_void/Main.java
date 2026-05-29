package io.gempba.seq.synthetic_void;

import java.util.ArrayList;
import java.util.List;

public class Main {

    // --- Configuration ---
    private static final int ARG_SIZE = 256;
    private static final int MAX_DEPTH = 10;
    // --------------------

    static void main() {

        // std::iota(v_initial_arg.begin(), v_initial_arg.end(), 0.0) → 0.0, 1.0, …, 255.0
        List<Double> initialArg = new ArrayList<>(ARG_SIZE);
        for (int i = 0; i < ARG_SIZE; i++) {
            initialArg.add((double) i);
        }

        System.out.printf("Running synthetic-tree benchmark (SEQ):%n");
        System.out.printf("  Depth = %d (leaves = %d)%n", MAX_DEPTH, 1 << MAX_DEPTH);

        Benchmark benchmark = new Benchmark();

        long startNs = System.nanoTime();
        long leafSum = benchmark.explore(0, MAX_DEPTH, initialArg);
        long elapsedNs = System.nanoTime() - startNs;

        System.out.printf("Leaf sum: %d%n", leafSum);
        System.out.printf("elapsed time: %.6f seconds%n", elapsedNs / 1e9);
    }
}

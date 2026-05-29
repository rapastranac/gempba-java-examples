package io.gempba.utils;

import java.util.List;
import java.util.Random;

public final class BenchmarkUtils {

    // --- Configuration ---
    public static final int INTERNAL_COST_US = 80;
    public static final int LEAF_COST_US = 5;
    public static final boolean RANDOMIZE_COST = true;
    public static final int ARG_SIZE = 256;
    public static final long RNG_SEED = 12345L;
    // --------------------

    private static final Random rng = new Random(RNG_SEED);

    private BenchmarkUtils() {
    }

    /**
     * Returns a random jitter in {@code [-30, 30]} — mirrors {@code jitter(rng)} in C++.
     */
    public static int jitter() {
        return rng.nextInt(61) - 30;
    }

    /**
     * Busy-waits for approximately {@code microseconds} µs.
     *
     * <p>Mirrors {@code busy_for_microseconds()} in C++.
     */
    public static void busyForMicroseconds(int microseconds) {
        if (microseconds <= 0) return;
        long start = System.nanoTime();
        long target = microseconds * 1_000L;
        //noinspection StatementWithEmptyBody
        while (System.nanoTime() - start < target) { /* busy wait */ }
    }

    /**
     * Applies a small trigonometric perturbation to every element of {@code arg}.
     *
     * <p>Mirrors {@code simulate_argument_build()} in C++.
     */
    public static void simulateArgumentBuild(List<Double> arg, int depth) {
        for (int i = 0; i < arg.size(); i++) {
            arg.set(i, Math.sin(arg.get(i) + depth * 0.001));
        }
    }
}

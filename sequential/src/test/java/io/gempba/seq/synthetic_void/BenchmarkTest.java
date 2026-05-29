package io.gempba.seq.synthetic_void;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property test: a perfect binary tree of depth d has exactly {@code 2^d}
 * leaves, regardless of the per-node cost simulation. Establishes that the
 * non-void leaf-sum return wires up correctly across recursion levels.
 */
class BenchmarkTest {

    private static List<Double> seedArg() {
        List<Double> arg = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            arg.add((double) i);
        }
        return arg;
    }

    @Test
    void leaf_count_matches_two_to_the_max_depth() {
        Benchmark benchmark = new Benchmark();
        for (int maxDepth = 0; maxDepth <= 8; maxDepth++) {
            long expected = 1L << maxDepth;
            long actual = benchmark.explore(0, maxDepth, seedArg());
            assertEquals(expected, actual,
                    "leaf sum at maxDepth=" + maxDepth + " should be 2^" + maxDepth);
        }
    }

    @Test
    void single_node_tree_returns_one_leaf() {
        Benchmark benchmark = new Benchmark();
        assertEquals(1L, benchmark.explore(0, 0, seedArg()));
    }
}

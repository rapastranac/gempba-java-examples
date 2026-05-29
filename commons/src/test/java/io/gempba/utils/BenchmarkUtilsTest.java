package io.gempba.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkUtilsTest {

    @Test
    void jitter_stays_within_minus_thirty_to_plus_thirty() {
        for (int i = 0; i < 10_000; i++) {
            int v = BenchmarkUtils.jitter();
            assertTrue(v >= -30 && v <= 30, "jitter() out of range: " + v);
        }
    }

    @Test
    void busyForMicroseconds_returns_immediately_for_non_positive_input() {
        long startZero = System.nanoTime();
        BenchmarkUtils.busyForMicroseconds(0);
        long elapsedZero = System.nanoTime() - startZero;
        assertTrue(elapsedZero < 1_000_000L, "busy(0) should be near-instant, got " + elapsedZero + " ns");

        long startNeg = System.nanoTime();
        BenchmarkUtils.busyForMicroseconds(-100);
        long elapsedNeg = System.nanoTime() - startNeg;
        assertTrue(elapsedNeg < 1_000_000L, "busy(-100) should be near-instant, got " + elapsedNeg + " ns");
    }

    @Test
    void busyForMicroseconds_busies_for_at_least_target_duration() {
        int target = 5_000;
        long start = System.nanoTime();
        BenchmarkUtils.busyForMicroseconds(target);
        long elapsedNs = System.nanoTime() - start;
        assertTrue(elapsedNs >= target * 1_000L,
                "busy(" + target + "us) returned after " + elapsedNs + " ns");
    }

    @Test
    void simulateArgumentBuild_replaces_every_element_with_sin_of_value_plus_offset() {
        List<Double> arg = new ArrayList<>(Arrays.asList(0.0, 1.0, 2.0, 3.0));
        int depth = 7;
        BenchmarkUtils.simulateArgumentBuild(arg, depth);

        double offset = depth * 0.001;
        assertEquals(Math.sin(0.0 + offset), arg.get(0), 1e-12);
        assertEquals(Math.sin(1.0 + offset), arg.get(1), 1e-12);
        assertEquals(Math.sin(2.0 + offset), arg.get(2), 1e-12);
        assertEquals(Math.sin(3.0 + offset), arg.get(3), 1e-12);
    }

    @Test
    void simulateArgumentBuild_handles_empty_list() {
        List<Double> arg = new ArrayList<>();
        assertDoesNotThrow(() -> BenchmarkUtils.simulateArgumentBuild(arg, 0));
        assertTrue(arg.isEmpty());
    }
}

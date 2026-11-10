package io.gempba.mp.stress;

import io.gempba.stress.StressRun;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@Tag("stress")
class StressTest {

    private static final int DEPTH = 16;
    private static final int THREADS_PER_RANK = 2;
    private static final int[] RANKS = {2, 4, 8};
    private static final Pattern RANK_LEAVES = Pattern.compile("^rank (\\d+): (\\d+) leaves\\s*$", Pattern.MULTILINE);

    @TestFactory
    Stream<DynamicTest> leafVisitor() {
        return IntStream.rangeClosed(1, StressRun.repeat()).boxed().flatMap(pass -> IntStream.of(RANKS).mapToObj(ranks -> {
            String name = "stress_multiprocessing_" + ranks + "_ranks-pass" + pass;
            return dynamicTest(name, () -> runOnce(name, ranks));
        }));
    }

    private static void runOnce(String name, int ranks) throws Exception {
        StressRun.Outcome outcome = StressRun.run(name, StressRun.mpiCommand(ranks, StressRun.javaCommand(
                LeafVisitor.class.getName(), Integer.toString(THREADS_PER_RANK), Integer.toString(DEPTH))));
        StressRun.assertPassed(outcome);

        Map<Integer, Long> leavesPerRank = new TreeMap<>();
        Matcher matcher = RANK_LEAVES.matcher(outcome.output());
        while (matcher.find()) {
            if (leavesPerRank.put(Integer.parseInt(matcher.group(1)), Long.parseLong(matcher.group(2))) != null) {
                fail(name + ": rank " + matcher.group(1) + " reported its leaves twice" + StressRun.describe(outcome));
            }
        }
        assertEquals(ranks, leavesPerRank.size(),
                name + ": expected every rank to report its leaves, got " + leavesPerRank + StressRun.describe(outcome));

        long leaves = leavesPerRank.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(1L << DEPTH, leaves,
                name + ": wrong leaf count, leaves per rank " + leavesPerRank + StressRun.describe(outcome));
    }
}

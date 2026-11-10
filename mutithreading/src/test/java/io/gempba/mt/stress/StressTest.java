package io.gempba.mt.stress;

import io.gempba.stress.StressRun;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@Tag("stress")
class StressTest {

    private static final int DEPTH = 16;
    private static final int[] THREADS = {2, 8, 16};

    @TestFactory
    Stream<DynamicTest> leafVisitor() {
        return IntStream.rangeClosed(1, StressRun.repeat()).boxed().flatMap(pass -> IntStream.of(THREADS).mapToObj(threads -> {
            String name = "stress_multithreading_" + threads + "_threads-pass" + pass;
            return dynamicTest(name, () -> StressRun.assertPassed(StressRun.run(name,
                    StressRun.javaCommand(LeafVisitor.class.getName(), Integer.toString(threads), Integer.toString(DEPTH)))));
        }));
    }
}

package io.gempba.stress;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs one stress program in its own process tree with a hard timeout. A hang in native code cannot be interrupted
 * from the test's JVM, so every run is a child process that is killed, descendants included, once its timeout expires.
 */
public final class StressRun {

    public static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final Path LOG_DIR = Path.of("target", "stress-logs");
    private static final Path CLASSPATH_FILE = Path.of("target", "stress-classpath.txt");
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private static final int OUTPUT_TAIL_CHARS = 4000;

    public record Outcome(String name, boolean timedOut, int exitCode, String output) {
    }

    private StressRun() {
    }

    public static int repeat() {
        return Integer.getInteger("gempba.stress.repeat", 1);
    }

    public static List<String> javaCommand(String mainClass, String... args) throws IOException {
        String classpath = String.join(File.pathSeparator,
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                Path.of("target", "classes").toAbsolutePath().toString(),
                Files.readString(CLASSPATH_FILE).trim());

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        command.addAll(List.of(args));
        return command;
    }

    public static List<String> mpiCommand(int ranks, List<String> rankCommand) {
        String launcher = System.getProperty("gempba.mpi.launcher", WINDOWS ? "mpiexec" : "mpirun");
        String launcherArgs = System.getProperty("gempba.mpi.args", WINDOWS ? "" : "--oversubscribe");

        List<String> command = new ArrayList<>();
        command.add(launcher);
        Arrays.stream(launcherArgs.trim().split("\\s+")).filter(arg -> !arg.isEmpty()).forEach(command::add);
        command.add("-n");
        command.add(Integer.toString(ranks));
        command.addAll(rankCommand);
        return command;
    }

    public static Outcome run(String name, List<String> command) throws IOException, InterruptedException {
        Files.createDirectories(LOG_DIR);
        Path log = LOG_DIR.resolve(name + ".log");

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        process.getOutputStream().close();

        boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }

        String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        return new Outcome(name, !finished, finished ? process.exitValue() : -1, output);
    }

    public static void assertPassed(Outcome outcome) {
        if (outcome.timedOut()) {
            fail(outcome.name() + " did not finish within " + TIMEOUT.toSeconds() + " s" + describe(outcome));
        }
        if (outcome.exitCode() != 0) {
            fail(outcome.name() + " exited with code " + outcome.exitCode() + describe(outcome));
        }
    }

    public static String describe(Outcome outcome) {
        String output = outcome.output();
        String tail = output.length() > OUTPUT_TAIL_CHARS ? output.substring(output.length() - OUTPUT_TAIL_CHARS) : output;
        return "\n--- output ---\n" + tail;
    }
}

package dev.persefonia.automation.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public final class CommandRunner {
    private final Path directory;
    private final Path home;
    private final Duration timeout;
    private final Map<String, String> variables;

    public CommandRunner(Path directory, Path home, Duration timeout, Map<String, String> variables) {
        this.directory = directory.toAbsolutePath();
        this.home = home.toAbsolutePath();
        this.timeout = timeout;
        this.variables = Map.copyOf(variables);
    }

    public static CommandResult execute(ProcessBuilder builder) throws Exception {
        Path directory = builder.directory() == null ? Path.of("").toAbsolutePath() : builder.directory().toPath();
        Map<String, String> overrides = new HashMap<>();
        for (var entry : builder.environment().entrySet()) {
            if (!entry.getValue().equals(System.getenv(entry.getKey()))) {
                overrides.put(entry.getKey(), entry.getValue());
            }
        }
        Path home = Files.createTempDirectory("persefonia-command-home-");
        try {
            return new CommandRunner(directory, home, Duration.ofSeconds(30), overrides).run(builder.command());
        } finally {
            try (var paths = Files.walk(home)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public CommandResult run(String... arguments) throws Exception {
        return run(List.of(arguments));
    }

    public CommandResult run(List<String> arguments) throws Exception {
        Files.createDirectories(home);
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(arguments));
        builder.directory(directory.toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("LC_ALL", "C");
        environment.put("LANG", "C");
        environment.put("TZ", "UTC");
        environment.put("HOME", home.toString());
        environment.put("TMPDIR", home.toString());
        environment.putAll(variables);
        Process process = builder.start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread out = Thread.ofVirtual().start(() -> copy(process.getInputStream(), stdout));
        Thread err = Thread.ofVirtual().start(() -> copy(process.getErrorStream(), stderr));
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        out.join(5000);
        err.join(5000);
        String output = stdout.toString(StandardCharsets.UTF_8);
        String errors = stderr.toString(StandardCharsets.UTF_8);
        if (!completed) {
            throw new AssertionError("Command timed out after " + timeout + ": " + arguments
                    + "\nstdout:\n" + output + "\nstderr:\n" + errors);
        }
        return new CommandResult(process.exitValue(), output, errors);
    }

    private static void copy(java.io.InputStream input, ByteArrayOutputStream target) {
        try (input) {
            input.transferTo(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not capture command output", exception);
        }
    }
}

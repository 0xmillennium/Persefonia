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

    public static Command command(String executable) {
        return new Command(executable);
    }

    public static final class Command {
        private final List<String> arguments = new ArrayList<>();
        private final Map<String, String> variables = new HashMap<>();
        private Path directory = Path.of("").toAbsolutePath();
        private Duration timeout = Duration.ofSeconds(30);
        private String path = "/usr/bin:/bin";

        private Command(String executable) { arguments.add(executable); }
        public Command args(String... values) { arguments.addAll(List.of(values)); return this; }
        public Command args(List<String> values) { arguments.addAll(values); return this; }
        public Command env(String key, String value) { variables.put(key, value); return this; }
        public Command env(Map<String, String> values) { variables.putAll(values); return this; }
        public Command pathPrepend(Path bin) { path = bin + ":" + path; return this; }
        public Command path(String value) { path = value; return this; }
        public Command directory(Path value) { directory = value; return this; }
        public Command timeout(Duration value) { timeout = value; return this; }

        public CommandResult run() throws Exception {
            Path home = Files.createTempDirectory("persefonia-command-home-");
            try {
                Map<String, String> environment = new HashMap<>(variables);
                environment.put("PATH", path);
                return new CommandRunner(directory, home, timeout, environment).run(arguments);
            } finally {
                try (var paths = Files.walk(home)) {
                    for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(item);
                    }
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

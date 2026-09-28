package dev.persefonia.automation.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class InvocationLog {
    private InvocationLog() {}

    public static List<List<String>> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        String data = Files.readString(file, StandardCharsets.UTF_8);
        if (data.isEmpty() || data.charAt(data.length() - 1) != '\0') {
            throw new IllegalArgumentException("Invocation log must end with NUL");
        }
        List<List<String>> calls = new ArrayList<>();
        String[] fields = data.split("\0", -1);
        int position = 0;
        while (position < fields.length - 1) {
            int count;
            try {
                count = Integer.parseInt(fields[position++]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invocation argument count is malformed", exception);
            }
            if (count < 0 || count > 1000 || position + count > fields.length - 1) {
                throw new IllegalArgumentException("Invocation argument count is out of range");
            }
            List<String> arguments = new ArrayList<>();
            for (int index = 0; index < count; index++) arguments.add(fields[position++]);
            calls.add(List.copyOf(arguments));
        }
        return calls;
    }

    public static String argumentsAsLines(Path file) throws IOException {
        List<String> arguments = read(file).stream().flatMap(List::stream).toList();
        return arguments.isEmpty() ? "" : String.join("\n", arguments) + "\n";
    }

    public static String callsAsLines(Path file) throws IOException {
        List<String> calls = read(file).stream().map(arguments -> String.join(" ", arguments)).toList();
        return calls.isEmpty() ? "" : calls.stream().collect(Collectors.joining("\n", "", "\n"));
    }
}

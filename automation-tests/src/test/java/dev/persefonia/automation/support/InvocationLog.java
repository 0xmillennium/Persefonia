package dev.persefonia.automation.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InvocationLog {
    private InvocationLog() {}

    public static List<List<String>> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        String data = Files.readString(file, StandardCharsets.UTF_8);
        if (data.isEmpty() || data.charAt(data.length() - 1) != '\0') {
            throw new IllegalArgumentException("Invocation log must end with NUL");
        }
        List<List<String>> calls = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        for (String field : data.split("\0", -1)) {
            if (field.isEmpty()) {
                if (!arguments.isEmpty()) {
                    calls.add(List.copyOf(arguments));
                    arguments.clear();
                }
            } else {
                arguments.add(field);
            }
        }
        return calls;
    }
}

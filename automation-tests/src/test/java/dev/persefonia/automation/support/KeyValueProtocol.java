package dev.persefonia.automation.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyValueProtocol {
    private KeyValueProtocol() {}

    public static Map<String, String> parse(String data, List<String> orderedKeys) {
        if (!data.endsWith("\n") || data.contains("\r") || data.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Protocol must end in LF and contain no CR or NUL");
        }
        String[] lines = data.split("\n", -1);
        if (lines.length != orderedKeys.size() + 1 || !lines[lines.length - 1].isEmpty()) {
            throw new IllegalArgumentException("Protocol record count differs from required keys");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < orderedKeys.size(); index++) {
            String line = lines[index];
            int separator = line.indexOf('=');
            if (separator < 1) throw new IllegalArgumentException("Malformed protocol record");
            String key = line.substring(0, separator);
            if (!key.matches("[a-z_]+") || !key.equals(orderedKeys.get(index)) || fields.containsKey(key)) {
                throw new IllegalArgumentException("Unknown, repeated, or out-of-order protocol key: " + key);
            }
            fields.put(key, line.substring(separator + 1));
        }
        return Map.copyOf(fields);
    }
}

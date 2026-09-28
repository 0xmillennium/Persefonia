package dev.persefonia.automation.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class WorkflowDocument {
    private final Map<String, Object> root;

    private WorkflowDocument(Map<String, Object> root) {
        this.root = root;
    }

    public static WorkflowDocument open(String file) throws IOException {
        Path path = Path.of("../.github/workflows", file);
        Object yaml = new Load(LoadSettings.builder().build()).loadFromString(Files.readString(path));
        return new WorkflowDocument(map(yaml));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new IllegalArgumentException("Expected a YAML mapping with string keys: " + value);
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a YAML sequence: " + value);
        }
        return (List<Object>) list;
    }

    public Map<String, Object> root() { return root; }
    public Map<String, Object> triggers() { return map(root.get("on")); }
    public Map<String, Object> jobs() { return map(root.get("jobs")); }
    public Map<String, Object> job(String id) { return map(jobs().get(id)); }
    public List<Object> steps(String id) { return list(job(id).get("steps")); }
    public Map<String, Object> step(String jobId, String name) {
        return steps(jobId).stream().map(WorkflowDocument::map)
                .filter(step -> name.equals(step.get("name")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing step: " + jobId + "/" + name));
    }
}

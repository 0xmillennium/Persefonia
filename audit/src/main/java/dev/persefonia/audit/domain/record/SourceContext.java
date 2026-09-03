package dev.persefonia.audit.domain.record;

public record SourceContext(String value) {
    public SourceContext {
        value = AuditIdentifierPolicy.sourceContext(value);
    }

    public static SourceContext of(String value) {
        return new SourceContext(value);
    }
}

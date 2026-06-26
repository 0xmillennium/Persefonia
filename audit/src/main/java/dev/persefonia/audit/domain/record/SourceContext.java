package dev.persefonia.audit.domain.record;

public record SourceContext(String value) {
    public SourceContext {
        value = AuditSafeText.lowerToken(value, "source context");
    }

    public static SourceContext of(String value) {
        return new SourceContext(value);
    }
}

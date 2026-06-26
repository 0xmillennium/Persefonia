package dev.persefonia.audit.domain.record;

public record SourceType(String value) {
    public SourceType {
        value = AuditSafeText.lowerToken(value, "source type");
    }

    public static SourceType of(String value) {
        return new SourceType(value);
    }
}

package dev.persefonia.audit.domain.record;

public record SourceType(String value) {
    public SourceType {
        value = AuditIdentifierPolicy.sourceType(value);
    }

    public static SourceType of(String value) {
        return new SourceType(value);
    }
}

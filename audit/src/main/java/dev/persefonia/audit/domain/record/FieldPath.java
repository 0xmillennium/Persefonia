package dev.persefonia.audit.domain.record;

public record FieldPath(String value) {
    public FieldPath {
        value = AuditSafeText.fieldToken(value, "field path");
    }

    public static FieldPath of(String value) {
        return new FieldPath(value);
    }
}

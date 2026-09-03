package dev.persefonia.audit.domain.record;

public record FieldPath(String value) {
    public FieldPath {
        value = AuditStructuredKeyPolicy.fieldPath(value);
    }

    public static FieldPath of(String value) {
        return new FieldPath(value);
    }
}

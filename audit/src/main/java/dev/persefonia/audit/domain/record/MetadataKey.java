package dev.persefonia.audit.domain.record;

public record MetadataKey(String value) {
    public MetadataKey {
        value = AuditSafeText.fieldToken(value, "metadata key");
    }

    public static MetadataKey of(String value) {
        return new MetadataKey(value);
    }
}

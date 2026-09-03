package dev.persefonia.audit.domain.record;

public record MetadataKey(String value) {
    public MetadataKey {
        value = AuditStructuredKeyPolicy.metadataKey(value);
    }

    public static MetadataKey of(String value) {
        return new MetadataKey(value);
    }
}

package dev.persefonia.audit.domain.record;

import java.util.Objects;

public record AuditMetadataEntry(MetadataKey key, SafeMetadataValue value) {
    public AuditMetadataEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }

    public static AuditMetadataEntry of(MetadataKey key, SafeMetadataValue value) {
        return new AuditMetadataEntry(key, value);
    }
}

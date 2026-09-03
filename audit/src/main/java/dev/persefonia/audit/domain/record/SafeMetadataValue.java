package dev.persefonia.audit.domain.record;

public record SafeMetadataValue(String value) {
    public SafeMetadataValue {
        value = AuditValuePolicy.metadataValue(value);
    }

    public static SafeMetadataValue of(String value) {
        return new SafeMetadataValue(value);
    }
}

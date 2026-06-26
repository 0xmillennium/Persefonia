package dev.persefonia.audit.domain.record;

public record SafeMetadataValue(String value) {
    public SafeMetadataValue {
        value = AuditSafeText.safeValue(value, "metadata value");
    }

    public static SafeMetadataValue of(String value) {
        return new SafeMetadataValue(value);
    }
}

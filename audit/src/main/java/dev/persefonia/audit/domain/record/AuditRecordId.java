package dev.persefonia.audit.domain.record;

import java.util.Objects;
import java.util.UUID;

public record AuditRecordId(UUID value) {
    public AuditRecordId {
        Objects.requireNonNull(value, "value");
    }

    public static AuditRecordId from(UUID value) {
        return new AuditRecordId(value);
    }

    public static AuditRecordId newId() {
        return new AuditRecordId(UUID.randomUUID());
    }
}

package dev.persefonia.audit.domain.record;

import java.util.Objects;
import java.util.Optional;

/**
 * A single audited field change. The field path is required, and at least one of
 * the old or new value must be present. All values are safe, bounded text.
 */
public record AuditChange(FieldPath fieldPath, SafeAuditValue oldValue, SafeAuditValue newValue) {
    public AuditChange {
        Objects.requireNonNull(fieldPath, "fieldPath");
        if (oldValue == null && newValue == null) {
            throw new AuditValidationException("audit change requires an old value or a new value");
        }
    }

    public static AuditChange of(FieldPath fieldPath, SafeAuditValue oldValue, SafeAuditValue newValue) {
        return new AuditChange(fieldPath, oldValue, newValue);
    }

    public Optional<SafeAuditValue> oldValueOptional() {
        return Optional.ofNullable(oldValue);
    }

    public Optional<SafeAuditValue> newValueOptional() {
        return Optional.ofNullable(newValue);
    }
}

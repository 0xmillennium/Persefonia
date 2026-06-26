package dev.persefonia.audit.application.query;

import java.util.Optional;

/**
 * Safe read model for a single audited field change. It exposes plain strings
 * only and never JDBC rows or mutable aggregate internals.
 */
public record AuditChangeView(String fieldPath, String oldValue, String newValue) {
    public Optional<String> oldValueOptional() {
        return Optional.ofNullable(oldValue);
    }

    public Optional<String> newValueOptional() {
        return Optional.ofNullable(newValue);
    }
}

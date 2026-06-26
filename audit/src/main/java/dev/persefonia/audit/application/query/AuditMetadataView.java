package dev.persefonia.audit.application.query;

/**
 * Safe read model for a single audit metadata entry. It exposes plain strings
 * only and never JDBC rows or mutable aggregate internals.
 */
public record AuditMetadataView(String key, String value) {
}

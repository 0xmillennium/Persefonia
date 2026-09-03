package dev.persefonia.audit.application.query;

import java.util.List;
import java.util.Objects;

/** Immutable page of compact Audit list rows. */
public record AuditRecordListPage(
        List<AuditRecordListItem> items,
        int page,
        int pageSize,
        long totalItems) {
    public AuditRecordListPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (page < 1 || pageSize < 1 || totalItems < 0) {
            throw new IllegalArgumentException("audit list page values must be non-negative and page-based");
        }
    }
}

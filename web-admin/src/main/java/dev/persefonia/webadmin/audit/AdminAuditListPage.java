package dev.persefonia.webadmin.audit;

import dev.persefonia.audit.application.query.AuditRecordListPage;
import java.util.Objects;

public record AdminAuditListPage(
        AdminAuditPageChrome chrome,
        AuditRecordListPage records,
        AdminAuditFilterView filters) {
    public AdminAuditListPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(filters, "filters");
    }

    public long totalPages() {
        return records.totalItems() == 0 ? 1 : (records.totalItems() + records.pageSize() - 1) / records.pageSize();
    }
    public boolean hasPreviousPage() { return records.page() > 1; }
    public boolean hasNextPage() { return records.page() < totalPages(); }
    public String previousUrl() { return filters.pageUrl(Math.max(1, records.page() - 1)); }
    public String nextUrl() { return filters.pageUrl(records.page() + 1); }
}

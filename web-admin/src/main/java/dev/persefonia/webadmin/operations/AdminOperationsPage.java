package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.application.operations.*;
import java.util.Objects;
import org.springframework.web.util.UriComponentsBuilder;

public record AdminOperationsPage(
        AdminOperationsPageChrome chrome,
        OperationsHealthSnapshot health,
        CacheInvalidationOperationsSummary summary,
        CacheInvalidationOperationsListPage batches,
        CacheInvalidationStatusFilter filter) {
    public AdminOperationsPage {
        Objects.requireNonNull(chrome, "chrome"); Objects.requireNonNull(health, "health");
        Objects.requireNonNull(summary, "summary"); Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(filter, "filter");
    }
    public long totalPages() { return batches.totalItems() == 0 ? 1 : (batches.totalItems() + batches.pageSize() - 1) / batches.pageSize(); }
    public boolean hasPreviousPage() { return batches.page() > 1; }
    public boolean hasNextPage() { return batches.page() < totalPages(); }
    public String previousUrl() { return pageUrl(Math.max(1, batches.page() - 1)); }
    public String nextUrl() { return pageUrl(batches.page() + 1); }
    private String pageUrl(int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/operations")
                .queryParam("page", page).queryParam("pageSize", batches.pageSize());
        if (!filter.value().isEmpty()) builder.queryParam("status", filter.value());
        return builder.build().encode().toUriString();
    }
}

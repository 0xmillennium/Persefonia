package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.application.operations.CacheInvalidationOperationsDetail;
import java.util.Objects;
import org.springframework.web.util.UriComponentsBuilder;

public record AdminOperationsDetailPage(
        AdminOperationsPageChrome chrome,
        CacheInvalidationOperationsDetail batch) {
    public AdminOperationsDetailPage {
        Objects.requireNonNull(chrome, "chrome"); Objects.requireNonNull(batch, "batch");
    }
    public String auditUrl() {
        return UriComponentsBuilder.fromPath("/admin/audit")
                .queryParam("entityContext", "platform_operations")
                .queryParam("entityType", "cache_invalidation_batch")
                .queryParam("entityId", batch.id().value())
                .build().encode().toUriString();
    }
}

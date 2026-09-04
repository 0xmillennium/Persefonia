package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.util.Objects;

public record OperationsHealthSnapshot(
        OperationsComponentStatus application,
        OperationsComponentStatus database,
        OperationsComponentStatus redis,
        CachePurgeProvider cacheProvider,
        OperationsComponentStatus cacheProviderReadiness,
        MigrationStatusSummary migrations) {
    public OperationsHealthSnapshot {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(cacheProvider, "cacheProvider");
        Objects.requireNonNull(cacheProviderReadiness, "cacheProviderReadiness");
        Objects.requireNonNull(migrations, "migrations");
    }
}

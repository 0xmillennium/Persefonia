package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.app.platformoperations.cache.config.CachePurgeProperties;
import dev.persefonia.platformoperations.application.operations.*;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Component;

@Component
public final class ActuatorOperationsHealthAdapter implements OperationsHealthQueryPort {
    private final ObjectProvider<HealthEndpoint> health;
    private final FlywayMigrationStatusAdapter migrations;
    private final CachePurgeProperties cachePurge;

    public ActuatorOperationsHealthAdapter(
            ObjectProvider<HealthEndpoint> health,
            FlywayMigrationStatusAdapter migrations,
            CachePurgeProperties cachePurge) {
        this.health = Objects.requireNonNull(health, "health");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.cachePurge = Objects.requireNonNull(cachePurge, "cachePurge");
    }

    @Override
    public OperationsHealthSnapshot snapshot() {
        HealthEndpoint endpoint = health.getIfAvailable();
        return new OperationsHealthSnapshot(
                component(endpoint, null),
                component(endpoint, "db"),
                component(endpoint, "redis"),
                cachePurge.getProvider(),
                cachePurge.getProvider() == null ? OperationsComponentStatus.UNKNOWN : OperationsComponentStatus.UP,
                migrations.status());
    }

    private static OperationsComponentStatus component(HealthEndpoint endpoint, String path) {
        if (endpoint == null) return OperationsComponentStatus.UNKNOWN;
        try {
            HealthDescriptor descriptor = path == null ? endpoint.health() : endpoint.healthForPath(path);
            if (descriptor == null || descriptor.getStatus() == null) return OperationsComponentStatus.UNKNOWN;
            return switch (descriptor.getStatus().getCode().toUpperCase(Locale.ROOT)) {
                case "UP" -> OperationsComponentStatus.UP;
                case "DOWN", "OUT_OF_SERVICE" -> OperationsComponentStatus.DOWN;
                case "DEGRADED" -> OperationsComponentStatus.DEGRADED;
                default -> OperationsComponentStatus.UNKNOWN;
            };
        } catch (RuntimeException exception) {
            return OperationsComponentStatus.UNKNOWN;
        }
    }
}

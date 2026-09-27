package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.operations.MigrationStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class DatabaseMigrationsHealthIndicator implements HealthIndicator {
    private final FlywayMigrationStatusAdapter migrations;

    public DatabaseMigrationsHealthIndicator(FlywayMigrationStatusAdapter migrations) {
        this.migrations = migrations;
    }

    @Override
    public Health health() {
        return migrations.status().status() == MigrationStatus.UP_TO_DATE
                ? Health.up().build() : Health.down().build();
    }
}

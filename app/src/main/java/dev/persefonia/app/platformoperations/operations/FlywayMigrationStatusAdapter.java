package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.operations.MigrationStatus;
import dev.persefonia.platformoperations.application.operations.MigrationStatusSummary;
import java.util.Arrays;
import java.util.Comparator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class FlywayMigrationStatusAdapter {
    private final ObjectProvider<Flyway> flyway;

    public FlywayMigrationStatusAdapter(ObjectProvider<Flyway> flyway) {
        this.flyway = flyway;
    }

    public MigrationStatusSummary status() {
        Flyway available = flyway.getIfAvailable();
        if (available == null) return unknown();
        try {
            var info = available.info();
            MigrationInfo current = info.current();
            MigrationInfo[] pending = info.pending();
            boolean failed = Arrays.stream(info.all()).anyMatch(item -> item.getState().isFailed());
            String latest = Arrays.stream(info.all())
                    .filter(item -> item.getVersion() != null && item.getState().isResolved())
                    .max(Comparator.comparing(MigrationInfo::getVersion))
                    .map(item -> item.getVersion().getVersion())
                    .orElse(null);
            MigrationStatus status = failed ? MigrationStatus.FAILED
                    : pending.length > 0 ? MigrationStatus.PENDING : MigrationStatus.UP_TO_DATE;
            return new MigrationStatusSummary(
                    current == null || current.getVersion() == null ? null : current.getVersion().getVersion(),
                    latest, pending.length, status);
        } catch (RuntimeException exception) {
            return unknown();
        }
    }

    private static MigrationStatusSummary unknown() {
        return new MigrationStatusSummary(null, null, 0, MigrationStatus.UNKNOWN);
    }
}

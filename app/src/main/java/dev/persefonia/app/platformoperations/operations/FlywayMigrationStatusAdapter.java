package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.operations.MigrationStatus;
import dev.persefonia.platformoperations.application.operations.MigrationStatusSummary;
import java.util.Arrays;
import java.util.Comparator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
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
            MigrationInfo[] all = info.all();
            MigrationVersion currentVersion = current == null ? null : current.getVersion();
            boolean failed = Arrays.stream(all).anyMatch(item -> item.getState().isFailed());
            MigrationVersion latest = Arrays.stream(all)
                    .filter(item -> item.getVersion() != null && item.getState().isResolved())
                    .max(Comparator.comparing(MigrationInfo::getVersion))
                    .map(MigrationInfo::getVersion)
                    .orElse(null);
            boolean upToDate = latest != null && latest.equals(currentVersion)
                    && Arrays.stream(all).allMatch(FlywayMigrationStatusAdapter::isHealthyHistoryEntry);
            MigrationStatus status = failed ? MigrationStatus.FAILED
                    : pending.length > 0 ? MigrationStatus.PENDING
                    : upToDate ? MigrationStatus.UP_TO_DATE : MigrationStatus.UNKNOWN;
            return new MigrationStatusSummary(
                    currentVersion == null ? null : currentVersion.getVersion(),
                    latest == null ? null : latest.getVersion(), pending.length, status);
        } catch (RuntimeException exception) {
            return unknown();
        }
    }

    private static boolean isHealthyHistoryEntry(MigrationInfo migration) {
        MigrationState state = migration.getState();
        // Superseded repeatable runs are normal history once a newer run has succeeded.
        return state == MigrationState.SUCCESS || state == MigrationState.SUPERSEDED;
    }

    private static MigrationStatusSummary unknown() {
        return new MigrationStatusSummary(null, null, 0, MigrationStatus.UNKNOWN);
    }
}

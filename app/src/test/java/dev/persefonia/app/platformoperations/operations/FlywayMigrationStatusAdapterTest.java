package dev.persefonia.app.platformoperations.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.platformoperations.application.operations.MigrationStatus;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Status;

class FlywayMigrationStatusAdapterTest {
    @Test
    void reportsUpToDatePendingFailedAndUnknownUsingOnlyBoundedFields() {
        MigrationInfo v20 = migration("20", MigrationState.SUCCESS);
        MigrationInfo v21 = migration("21", MigrationState.SUCCESS);
        assertThat(adapter(v21, List.of(), List.of(v20, v21)).status())
                .satisfies(status -> {
                    assertThat(status.currentAppliedVersion()).isEqualTo("21");
                    assertThat(status.latestResolvedVersion()).isEqualTo("21");
                    assertThat(status.pendingCount()).isZero();
                    assertThat(status.status()).isEqualTo(MigrationStatus.UP_TO_DATE);
                });

        MigrationInfo pendingV21 = migration("21", MigrationState.PENDING);
        assertThat(adapter(v20, List.of(pendingV21), List.of(v20, pendingV21)).status())
                .satisfies(status -> {
                    assertThat(status.currentAppliedVersion()).isEqualTo("20");
                    assertThat(status.latestResolvedVersion()).isEqualTo("21");
                    assertThat(status.pendingCount()).isEqualTo(1);
                    assertThat(status.status()).isEqualTo(MigrationStatus.PENDING);
                });

        MigrationInfo failedV21 = migration("21", MigrationState.FAILED);
        assertThat(adapter(v20, List.of(), List.of(v20, failedV21)).status().status())
                .isEqualTo(MigrationStatus.FAILED);

        @SuppressWarnings("unchecked")
        ObjectProvider<Flyway> unavailable = mock(ObjectProvider.class);
        when(unavailable.getIfAvailable()).thenReturn(null);
        assertThat(new FlywayMigrationStatusAdapter(unavailable).status().status())
                .isEqualTo(MigrationStatus.UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = MigrationState.class, names = {
            "IGNORED", "OUTDATED", "MISSING_SUCCESS", "FUTURE_SUCCESS", "ABOVE_TARGET",
            "BASELINE", "BELOW_BASELINE", "BASELINE_IGNORED", "UNDONE", "AVAILABLE", "OUT_OF_ORDER", "DELETED"
    })
    void driftWithoutPendingOrFailedMigrationsNeverReportsHealthy(MigrationState state) {
        MigrationInfo v21 = migration("21", MigrationState.SUCCESS);
        String version = switch (state) {
            case OUTDATED -> null;
            case FUTURE_SUCCESS, ABOVE_TARGET -> "22";
            default -> "20";
        };
        MigrationInfo drift = migration(version, state);
        var adapter = adapter(state == MigrationState.FUTURE_SUCCESS ? drift : v21,
                List.of(), List.of(drift, v21));

        assertThat(adapter.status()).satisfies(status -> {
            assertThat(status.pendingCount()).isZero();
            assertThat(status.status()).isEqualTo(MigrationStatus.UNKNOWN);
        });
        assertThat(new DatabaseMigrationsHealthIndicator(adapter).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @ParameterizedTest
    @CsvSource({"20,21", "22,21"})
    void mismatchedAppliedAndResolvedVersionsCannotReportUpToDate(String applied, String resolved) {
        assertThat(adapter(migration(applied, MigrationState.SUCCESS), List.of(),
                List.of(migration(resolved, MigrationState.SUCCESS))).status())
                .satisfies(status -> {
                    assertThat(status.currentAppliedVersion()).isEqualTo(applied);
                    assertThat(status.latestResolvedVersion()).isEqualTo(resolved);
                    assertThat(status.status()).isEqualTo(MigrationStatus.UNKNOWN);
                });
    }

    @Test
    void requiresKnownCurrentAndResolvedVersionBounds() {
        assertThat(adapter(null, List.of(), List.of()).status().status()).isEqualTo(MigrationStatus.UNKNOWN);

        MigrationInfo repeatable = migration(null, MigrationState.SUCCESS);
        assertThat(adapter(repeatable, List.of(), List.of(repeatable)).status().status())
                .isEqualTo(MigrationStatus.UNKNOWN);

        MigrationInfo missing = migration("21", MigrationState.MISSING_SUCCESS);
        assertThat(adapter(missing, List.of(), List.of(missing)).status()).satisfies(status -> {
            assertThat(status.currentAppliedVersion()).isEqualTo("21");
            assertThat(status.latestResolvedVersion()).isNull();
            assertThat(status.status()).isEqualTo(MigrationStatus.UNKNOWN);
        });

        MigrationInfo resolved = migration("21", MigrationState.SUCCESS);
        assertThat(adapter(null, List.of(), List.of(resolved)).status().status()).isEqualTo(MigrationStatus.UNKNOWN);
    }

    @Test
    void successfulHistoryCanIncludeSupersededRepeatableRuns() {
        MigrationInfo v21 = migration("21", MigrationState.SUCCESS);
        var adapter = adapter(v21, List.of(), List.of(v21,
                migration(null, MigrationState.SUPERSEDED), migration(null, MigrationState.SUCCESS)));

        assertThat(adapter.status().status()).isEqualTo(MigrationStatus.UP_TO_DATE);
        assertThat(new DatabaseMigrationsHealthIndicator(adapter).health().getStatus()).isEqualTo(Status.UP);
    }

    @ParameterizedTest
    @EnumSource(value = MigrationState.class, names = {"FAILED", "MISSING_FAILED", "FUTURE_FAILED"})
    void failedMigrationsTakePrecedenceOverPendingMigrations(MigrationState state) {
        MigrationInfo v21 = migration("21", MigrationState.SUCCESS);
        MigrationInfo pending = migration("22", MigrationState.PENDING);
        assertThat(adapter(v21, List.of(pending), List.of(migration("20", state), v21, pending)).status())
                .satisfies(status -> {
                    assertThat(status.pendingCount()).isEqualTo(1);
                    assertThat(status.status()).isEqualTo(MigrationStatus.FAILED);
                });
    }

    private static FlywayMigrationStatusAdapter adapter(
            MigrationInfo current, List<MigrationInfo> pending, List<MigrationInfo> all) {
        @SuppressWarnings("unchecked")
        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(provider.getIfAvailable()).thenReturn(flyway);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(info.pending()).thenReturn(pending.toArray(MigrationInfo[]::new));
        when(info.all()).thenReturn(all.toArray(MigrationInfo[]::new));
        return new FlywayMigrationStatusAdapter(provider);
    }

    private static MigrationInfo migration(String version, MigrationState state) {
        MigrationInfo migration = mock(MigrationInfo.class);
        when(migration.getVersion()).thenReturn(version == null ? null : MigrationVersion.fromVersion(version));
        when(migration.getState()).thenReturn(state);
        return migration;
    }
}

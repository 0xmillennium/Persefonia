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
import org.springframework.beans.factory.ObjectProvider;

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
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(migration.getState()).thenReturn(state);
        return migration;
    }
}

package dev.persefonia.app.platformoperations.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.platformoperations.application.operations.MigrationStatus;
import dev.persefonia.platformoperations.application.operations.MigrationStatusSummary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.health.contributor.Status;

class DatabaseMigrationsHealthIndicatorTest {
    @ParameterizedTest
    @EnumSource(MigrationStatus.class)
    void onlyUpToDateMigrationsAreHealthy(MigrationStatus state) {
        var migrations = mock(FlywayMigrationStatusAdapter.class);
        when(migrations.status()).thenReturn(new MigrationStatusSummary(null, null, 0, state));

        var health = new DatabaseMigrationsHealthIndicator(migrations).health();

        assertThat(health.getStatus()).isEqualTo(state == MigrationStatus.UP_TO_DATE ? Status.UP : Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }
}

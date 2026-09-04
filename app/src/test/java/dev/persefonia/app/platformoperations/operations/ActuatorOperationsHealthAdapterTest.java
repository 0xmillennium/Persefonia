package dev.persefonia.app.platformoperations.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.app.platformoperations.cache.config.CachePurgeProperties;
import dev.persefonia.app.medialibrary.storage.MediaStorageReadinessService;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.contributor.Status;

class ActuatorOperationsHealthAdapterTest {
    @Test
    void projectsOnlyBoundedApplicationDatabaseRedisProviderAndMigrationState() {
        @SuppressWarnings("unchecked")
        ObjectProvider<HealthEndpoint> provider = mock(ObjectProvider.class);
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        when(provider.getIfAvailable()).thenReturn(endpoint);
        HealthDescriptor application = health(Status.UP);
        HealthDescriptor database = health(Status.DOWN);
        HealthDescriptor redis = health(Status.UNKNOWN);
        when(endpoint.health()).thenReturn(application);
        when(endpoint.healthForPath("db")).thenReturn(database);
        when(endpoint.healthForPath("redis")).thenReturn(redis);
        FlywayMigrationStatusAdapter migrations = mock(FlywayMigrationStatusAdapter.class);
        MigrationStatusSummary migrationStatus = new MigrationStatusSummary("20", "21", 1, MigrationStatus.PENDING);
        when(migrations.status()).thenReturn(migrationStatus);
        CachePurgeProperties properties = new CachePurgeProperties();
        properties.setProvider(CachePurgeProvider.CLOUDFLARE);

        OperationsHealthSnapshot snapshot =
                new ActuatorOperationsHealthAdapter(provider, migrations, properties, readyMedia()).snapshot();

        assertThat(snapshot).isEqualTo(new OperationsHealthSnapshot(
                OperationsComponentStatus.UP,
                OperationsComponentStatus.DOWN,
                OperationsComponentStatus.UNKNOWN,
                OperationsComponentStatus.UP,
                CachePurgeProvider.CLOUDFLARE,
                OperationsComponentStatus.UP,
                migrationStatus));
    }

    @Test
    void missingOrFailingHealthInfrastructureIsReportedAsUnknown() {
        @SuppressWarnings("unchecked")
        ObjectProvider<HealthEndpoint> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FlywayMigrationStatusAdapter migrations = mock(FlywayMigrationStatusAdapter.class);
        when(migrations.status()).thenReturn(new MigrationStatusSummary(null, null, 0, MigrationStatus.UNKNOWN));

        OperationsHealthSnapshot snapshot = new ActuatorOperationsHealthAdapter(
                provider, migrations, new CachePurgeProperties(), emptyMedia()).snapshot();

        assertThat(snapshot.application()).isEqualTo(OperationsComponentStatus.UNKNOWN);
        assertThat(snapshot.database()).isEqualTo(OperationsComponentStatus.UNKNOWN);
        assertThat(snapshot.redis()).isEqualTo(OperationsComponentStatus.UNKNOWN);
    }

    private static HealthDescriptor health(Status status) {
        HealthDescriptor descriptor = mock(IndicatedHealthDescriptor.class);
        when(descriptor.getStatus()).thenReturn(status);
        return descriptor;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MediaStorageReadinessService> emptyMedia() {
        ObjectProvider<MediaStorageReadinessService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MediaStorageReadinessService> readyMedia() {
        ObjectProvider<MediaStorageReadinessService> provider = mock(ObjectProvider.class);
        MediaStorageReadinessService readiness = mock(MediaStorageReadinessService.class);
        when(readiness.isRuntimeReady()).thenReturn(true);
        when(provider.getIfAvailable()).thenReturn(readiness);
        return provider;
    }
}

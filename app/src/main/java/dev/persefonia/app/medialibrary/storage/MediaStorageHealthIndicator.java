package dev.persefonia.app.medialibrary.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

final class MediaStorageHealthIndicator implements HealthIndicator {
    private final MediaStorageReadinessService readiness;

    MediaStorageHealthIndicator(MediaStorageReadinessService readiness) {
        this.readiness = readiness;
    }

    @Override
    public Health health() {
        return readiness.isRuntimeReady() ? Health.up().build() : Health.down().build();
    }
}

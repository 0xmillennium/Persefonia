package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class MediaStorageHealthIndicatorTest {
    @Test
    void reportsUpWhenMediaRuntimeIsReady() {
        MediaStorageReadinessService readiness = mock(MediaStorageReadinessService.class);
        when(readiness.isRuntimeReady()).thenReturn(true);

        assertThat(new MediaStorageHealthIndicator(readiness).health().getStatus().getCode())
                .isEqualTo("UP");
    }

    @Test
    void reportsDownWithoutSensitiveDetailsWhenMediaRuntimeIsUnavailable() {
        MediaStorageReadinessService readiness = mock(MediaStorageReadinessService.class);
        when(readiness.isRuntimeReady()).thenReturn(false);

        var health = new MediaStorageHealthIndicator(readiness).health();
        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).isEmpty();
    }
}

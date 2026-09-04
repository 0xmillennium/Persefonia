package dev.persefonia.app.platformoperations.cache.execution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.platformoperations.domain.cache.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CachePurgeMetricsTest {
    @Test
    void recordsOnlyBoundedProviderResultAndFailureCategories() {
        var registry = new SimpleMeterRegistry();
        var metrics = new CachePurgeMetrics(registry);

        metrics.providerExecution(CachePurgeProvider.CLOUDFLARE, CachePurgeResult.SUCCESS);
        metrics.providerFailure(CachePurgeProvider.CLOUDFLARE, CachePurgeFailureReason.TIMEOUT);
        metrics.reservationFailure();
        metrics.resultPersistenceFailure();

        assertThat(registry.get("persefonia.cache.purge.executions")
                .tags("provider", "CLOUDFLARE", "result", "SUCCESS").counter().count()).isEqualTo(1);
        assertThat(registry.get("persefonia.cache.purge.failures")
                .tags("provider", "CLOUDFLARE", "failure_reason", "TIMEOUT").counter().count()).isEqualTo(1);
        assertThat(registry.get("persefonia.cache.purge.reservation.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("persefonia.cache.purge.result.persistence.failures").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isIn("provider", "result", "failure_reason")));
    }
}

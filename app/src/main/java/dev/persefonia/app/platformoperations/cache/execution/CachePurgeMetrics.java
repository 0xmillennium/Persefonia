package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class CachePurgeMetrics {
    private final MeterRegistry registry;
    private final Counter reservationFailures;
    private final Counter resultPersistenceFailures;

    public CachePurgeMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.reservationFailures = registry.counter("persefonia.cache.purge.reservation.failures");
        this.resultPersistenceFailures = registry.counter("persefonia.cache.purge.result.persistence.failures");
    }

    public void providerExecution(CachePurgeProvider provider, CachePurgeResult result) {
        registry.counter("persefonia.cache.purge.executions",
                "provider", provider.name(), "result", result.name()).increment();
    }

    public void providerFailure(CachePurgeProvider provider, CachePurgeFailureReason reason) {
        registry.counter("persefonia.cache.purge.failures",
                "provider", provider.name(), "failure_reason", reason.name()).increment();
    }

    public void reservationFailure() { reservationFailures.increment(); }
    public void resultPersistenceFailure() { resultPersistenceFailures.increment(); }
}

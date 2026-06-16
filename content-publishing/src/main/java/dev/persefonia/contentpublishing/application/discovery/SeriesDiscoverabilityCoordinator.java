package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.application.exception.ContentDiscoverySynchronizationException;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import java.util.Objects;

public final class SeriesDiscoverabilityCoordinator {
    private final UpdateDiscoverableResourcePort updatePort;
    private final RemoveDiscoverableResourcePort removePort;
    private final SeriesDiscoveryProjectionFactory projectionFactory;

    public SeriesDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            SeriesDiscoveryProjectionFactory projectionFactory) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.removePort = Objects.requireNonNull(removePort, "removePort");
        this.projectionFactory = Objects.requireNonNull(projectionFactory, "projectionFactory");
    }

    public void sync(Series series) {
        Objects.requireNonNull(series, "series");
        projectionFactory.projectionFor(series)
                .ifPresentOrElse(
                        input -> requireSuccess(updatePort.update(input)),
                        () -> requireSuccess(removePort.remove(projectionFactory.removeCommandFor(series))));
    }

    private static void requireSuccess(DiscoverableResourceProjectionResult result) {
        if (result instanceof DiscoverableResourceProjectionResult.Rejected rejected) {
            throw new ContentDiscoverySynchronizationException(
                    "Discovery projection synchronization was rejected: " + rejected.reason());
        }
    }
}

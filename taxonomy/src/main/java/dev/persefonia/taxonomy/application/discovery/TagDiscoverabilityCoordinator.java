package dev.persefonia.taxonomy.application.discovery;

import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.taxonomy.application.exception.TagDiscoverySynchronizationException;
import dev.persefonia.taxonomy.domain.model.Tag;
import java.util.Objects;

public final class TagDiscoverabilityCoordinator {
    private final UpdateDiscoverableResourcePort updatePort;
    private final TagDiscoveryProjectionFactory projectionFactory;

    public TagDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            TagDiscoveryProjectionFactory projectionFactory) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.projectionFactory = Objects.requireNonNull(projectionFactory, "projectionFactory");
    }

    public void sync(Tag tag) {
        projectionFactory.projectionsFor(tag).forEach(input -> requireSuccess(updatePort.update(input)));
    }

    private static void requireSuccess(DiscoverableResourceProjectionResult result) {
        if (result instanceof DiscoverableResourceProjectionResult.Rejected rejected) {
            throw new TagDiscoverySynchronizationException(
                    "Discovery projection synchronization was rejected: " + rejected.reason());
        }
    }
}

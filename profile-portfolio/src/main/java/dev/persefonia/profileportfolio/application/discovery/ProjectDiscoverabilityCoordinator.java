package dev.persefonia.profileportfolio.application.discovery;

import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.profileportfolio.application.exception.ProjectDiscoverySynchronizationException;
import dev.persefonia.profileportfolio.domain.project.Project;
import java.util.Objects;

public final class ProjectDiscoverabilityCoordinator {
    private final UpdateDiscoverableResourcePort updatePort;
    private final RemoveDiscoverableResourcePort removePort;
    private final ProjectDiscoveryProjectionFactory projectionFactory;

    public ProjectDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            ProjectDiscoveryProjectionFactory projectionFactory) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.removePort = Objects.requireNonNull(removePort, "removePort");
        this.projectionFactory = Objects.requireNonNull(projectionFactory, "projectionFactory");
    }

    public void sync(Project project) {
        Objects.requireNonNull(project, "project");
        requireSuccess(removePort.remove(projectionFactory.removeCommandFor(project)));
        for (var projection : projectionFactory.projectionsFor(project)) {
            requireSuccess(updatePort.update(projection));
        }
    }

    private static void requireSuccess(DiscoverableResourceProjectionResult result) {
        if (result instanceof DiscoverableResourceProjectionResult.Rejected rejected) {
            throw new ProjectDiscoverySynchronizationException(
                    "Discovery projection synchronization was rejected: " + rejected.reason());
        }
    }
}

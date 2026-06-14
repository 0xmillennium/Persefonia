package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;

/**
 * Idempotently removes current projections for a source reference.
 */
public interface RemoveDiscoverableResourcePort {
    DiscoverableResourceProjectionResult remove(RemoveDiscoverableResourceCommand command);
}

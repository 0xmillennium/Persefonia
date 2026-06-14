package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;

/**
 * Creates or replaces the current discoverable projection for source input.
 * Implementations own aggregate creation and replacement.
 */
public interface UpdateDiscoverableResourcePort {
    DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input);
}

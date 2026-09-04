package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.operations.CacheOperationsCommandActor;
import dev.persefonia.platformoperations.application.operations.CacheOperationsCommandAuthorizationPolicy;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public final class IdentityAccessCacheOperationsCommandAuthorizationPolicy
        implements CacheOperationsCommandAuthorizationPolicy {
    @Override
    public void requireOwner(CacheOperationsCommandActor actor, String commandName) {
        Objects.requireNonNull(commandName, "commandName");
        if (actor == null || !actor.active() || !actor.owner()) {
            throw new AccessDeniedException("OWNER authorization is required for cache operations command");
        }
    }
}

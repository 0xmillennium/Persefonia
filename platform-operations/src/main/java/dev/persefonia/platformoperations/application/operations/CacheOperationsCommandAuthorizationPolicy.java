package dev.persefonia.platformoperations.application.operations;

public interface CacheOperationsCommandAuthorizationPolicy {
    void requireOwner(CacheOperationsCommandActor actor, String commandName);
}

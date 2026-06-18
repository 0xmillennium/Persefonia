package dev.persefonia.medialibrary.application.authorization;

public interface MediaCommandAuthorizationPolicy {
    void requireOwner(MediaCommandActor actor, String commandName);
}

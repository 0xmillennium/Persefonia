package dev.persefonia.discovery.application.authorization;

public interface AdminRedirectCommandAuthorizationPolicy {
    void requireOwner(AdminRedirectCommandActor actor, String commandName);
}

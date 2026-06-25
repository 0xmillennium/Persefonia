package dev.persefonia.communication.application.authorization;

public interface ContactMessageCommandAuthorizationPolicy {
    void requireOwner(ContactMessageCommandActor actor, String commandName);
}

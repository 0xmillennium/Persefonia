package dev.persefonia.contentpublishing.application.authorization;

public interface ContentCommandAuthorizationPolicy {
    void requireOwner(ContentCommandActor actor, String commandName);
}

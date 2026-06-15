package dev.persefonia.taxonomy.application.authorization;

public interface TaxonomyCommandAuthorizationPolicy {
    void requireOwner(TaxonomyCommandActor actor, String commandName);
}

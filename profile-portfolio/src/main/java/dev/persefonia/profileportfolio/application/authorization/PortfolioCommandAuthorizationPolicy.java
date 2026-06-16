package dev.persefonia.profileportfolio.application.authorization;

public interface PortfolioCommandAuthorizationPolicy {
    void requireOwner(PortfolioCommandActor actor, String commandName);
}

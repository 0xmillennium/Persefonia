package dev.persefonia.webadmin.settings;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import org.springframework.security.core.Authentication;

public interface PortfolioAdminActorResolver {
    PortfolioCommandActor resolve(Authentication authentication);
}

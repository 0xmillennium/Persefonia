package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.app.security.admin.PersefoniaAdminCommandActorResolver;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.webadmin.discovery.AdminRedirectAccessPolicy;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaAdminRedirectAccessPolicy implements AdminRedirectAccessPolicy {
    private final PersefoniaAdminCommandActorResolver actors;
    private final AdminCommandAuthorizationPolicy authorization;

    public PersefoniaAdminRedirectAccessPolicy(
            PersefoniaAdminCommandActorResolver actors,
            AdminCommandAuthorizationPolicy authorization) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public void requireOwner(Authentication authentication, String commandName) {
        authorization.requireOwner(actors.resolve(authentication), AdminCommand.named(commandName));
    }
}

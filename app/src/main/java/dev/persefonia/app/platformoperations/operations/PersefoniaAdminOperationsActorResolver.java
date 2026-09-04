package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.platformoperations.application.operations.CacheOperationsCommandActor;
import dev.persefonia.webadmin.operations.AdminOperationsActorResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaAdminOperationsActorResolver implements AdminOperationsActorResolver {
    @Override
    public CacheOperationsCommandActor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser user)) {
            throw new AccessDeniedException("Admin operations actor unavailable");
        }
        var principal = user.adminPrincipal();
        return new CacheOperationsCommandActor(
                principal.accountId().value(), true, principal.isOwner());
    }
}

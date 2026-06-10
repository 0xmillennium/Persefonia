package dev.persefonia.app.security.admin;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;

@Component
public final class PersefoniaAdminCommandActorResolver {
    public AdminCommandActor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw denied();
        }
        if (!(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw denied();
        }
        AdminPrincipal principal = oidcUser.adminPrincipal();
        return new AdminCommandActor(
                principal.accountId(),
                principal.status(),
                principal.roles());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Admin command actor unavailable");
    }
}

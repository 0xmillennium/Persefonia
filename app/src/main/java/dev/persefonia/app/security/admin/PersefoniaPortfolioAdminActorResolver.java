package dev.persefonia.app.security.admin;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.webadmin.settings.PortfolioAdminActorResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaPortfolioAdminActorResolver implements PortfolioAdminActorResolver {
    @Override
    public PortfolioCommandActor resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw new AccessDeniedException("Portfolio admin actor unavailable");
        }
        AdminPrincipal principal = oidcUser.adminPrincipal();
        return new PortfolioCommandActor(
                principal.accountId().value(),
                principal.status() == dev.persefonia.identityaccess.domain.admin.AdminAccountStatus.ACTIVE,
                principal.isOwner());
    }
}

package dev.persefonia.app.security.admin;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.webadmin.media.MediaAdminActorResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaMediaAdminActorResolver implements MediaAdminActorResolver {
    @Override
    public MediaCommandActor resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw new AccessDeniedException("Media admin actor unavailable");
        }
        AdminPrincipal principal = oidcUser.adminPrincipal();
        return new MediaCommandActor(
                principal.accountId().value(),
                principal.status() == AdminAccountStatus.ACTIVE,
                principal.isOwner());
    }
}

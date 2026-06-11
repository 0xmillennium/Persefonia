package dev.persefonia.app.security.admin;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.webadmin.content.ContentAdminActorResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaContentAdminActorResolver implements ContentAdminActorResolver {
    @Override
    public ContentCommandActor resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw new AccessDeniedException("Content admin actor unavailable");
        }
        AdminPrincipal principal = oidcUser.adminPrincipal();
        return new ContentCommandActor(
                AdminIdentityRef.from(principal.accountId().value()),
                principal.status() == dev.persefonia.identityaccess.domain.admin.AdminAccountStatus.ACTIVE,
                principal.isOwner());
    }
}

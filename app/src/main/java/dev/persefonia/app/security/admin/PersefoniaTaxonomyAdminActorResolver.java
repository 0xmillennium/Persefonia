package dev.persefonia.app.security.admin;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.webadmin.taxonomy.TaxonomyAdminActorResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class PersefoniaTaxonomyAdminActorResolver implements TaxonomyAdminActorResolver {
    @Override
    public TaxonomyCommandActor resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw new AccessDeniedException("Taxonomy admin actor unavailable");
        }
        AdminPrincipal principal = oidcUser.adminPrincipal();
        return new TaxonomyCommandActor(
                principal.accountId().value(),
                principal.status() == AdminAccountStatus.ACTIVE,
                principal.isOwner());
    }
}

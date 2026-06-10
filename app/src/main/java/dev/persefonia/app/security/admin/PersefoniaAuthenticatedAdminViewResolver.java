package dev.persefonia.app.security.admin;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.webadmin.AuthenticatedAdminView;
import dev.persefonia.webadmin.AuthenticatedAdminViewResolver;

@Component
final class PersefoniaAuthenticatedAdminViewResolver implements AuthenticatedAdminViewResolver {
    private static final String ACCESS_DENIED_MESSAGE = "Authenticated local admin is required";

    @Override
    public AuthenticatedAdminView resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PersefoniaOidcUser oidcUser)) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        AdminPrincipal adminPrincipal = oidcUser.adminPrincipal();
        if (adminPrincipal.status() != AdminAccountStatus.ACTIVE) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        List<String> roleLabels = adminPrincipal.roles().stream()
                .sorted()
                .map(PersefoniaAuthenticatedAdminViewResolver::roleLabel)
                .toList();
        return new AuthenticatedAdminView(
                adminPrincipal.displayName().value(),
                roleLabels,
                adminPrincipal.isOwner(),
                adminPrincipal.isEditor());
    }

    private static String roleLabel(AdminRole role) {
        return switch (role) {
            case OWNER -> "Owner";
            case EDITOR -> "Editor";
        };
    }
}

package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import dev.persefonia.identityaccess.domain.admin.AdminRole;

class PersefoniaAuthenticatedAdminViewResolverTest {
    private final PersefoniaAuthenticatedAdminViewResolver resolver =
            new PersefoniaAuthenticatedAdminViewResolver();

    @Test
    void resolvesPersefoniaOidcUserToAuthenticatedAdminView() {
        var view = resolver.resolve(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        assertThat(view.displayName()).isEqualTo("Ada Admin");
        assertThat(view.owner()).isTrue();
        assertThat(view.editor()).isFalse();
    }

    @Test
    void mapsOwnerRoleLabel() {
        assertThat(resolver.resolve(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)).roleLabels())
                .containsExactly("Owner");
    }

    @Test
    void mapsEditorRoleLabel() {
        assertThat(resolver.resolve(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)).roleLabels())
                .containsExactly("Editor");
    }

    @Test
    void rejectsNullAuthentication() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsUnauthenticatedAuthentication() {
        OAuth2AuthenticationToken authentication =
                AdminAuthenticationTestSupport.authentication(AdminRole.OWNER);
        authentication.setAuthenticated(false);

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsGenericPrincipal() {
        assertThatThrownBy(() -> resolver.resolve(new TestingAuthenticationToken("user", "password")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doesNotExposeEmailOrSubjectInExceptionMessage() {
        assertThatThrownBy(() -> resolver.resolve(new TestingAuthenticationToken("admin@example.com", "opaque-subject")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageNotContaining("admin@example.com")
                .hasMessageNotContaining("opaque-subject");
    }
}

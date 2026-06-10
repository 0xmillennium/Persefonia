package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class PersefoniaAdminCommandActorResolverTest {
    private final PersefoniaAdminCommandActorResolver resolver = new PersefoniaAdminCommandActorResolver();

    @Test
    void resolvesPersefoniaOidcUserToCommandActor() {
        var actor = resolver.resolve(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        assertThat(actor.isOwner()).isTrue();
        assertThat(actor.isActive()).isTrue();
    }

    @Test
    void resolvedActorContainsAccountIdStatusAndRoles() {
        OAuth2AuthenticationToken authentication = AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR);
        AdminPrincipal principal = ((PersefoniaOidcUser) authentication.getPrincipal()).adminPrincipal();

        var actor = resolver.resolve(authentication);

        assertThat(actor.accountId()).isEqualTo(principal.accountId());
        assertThat(actor.status()).isEqualTo(principal.status());
        assertThat(actor.roles()).containsExactlyElementsOf(principal.roles());
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
    void doesNotExposeAccountIdEmailSubjectOrTokenInExceptionMessage() {
        assertThatThrownBy(() -> resolver.resolve(new TestingAuthenticationToken(
                        "00000000-0000-0000-0000-000000000001",
                        "admin@example.com opaque-subject fake-id-token-value")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageNotContaining("00000000-0000-0000-0000-000000000001")
                .hasMessageNotContaining("admin@example.com")
                .hasMessageNotContaining("opaque-subject")
                .hasMessageNotContaining("fake-id-token-value");
    }

    @Test
    void doesNotCallRepositoryOrBootstrapService() {
        assertThat(PersefoniaAdminCommandActorResolver.class.getDeclaredFields()).isEmpty();
        assertThat(Set.of(PersefoniaAdminCommandActorResolver.class.getDeclaredConstructors()))
                .extracting(Constructor::getParameterCount)
                .containsExactly(0);
    }
}

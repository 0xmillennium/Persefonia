package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class AdminOidcSessionRevalidationFilterTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private final AdminOidcSessionRevalidationService service = mock(AdminOidcSessionRevalidationService.class);
    private final SecurityContextRepository contexts = spy(new org.springframework.security.web.context.DelegatingSecurityContextRepository(
            new org.springframework.security.web.context.RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository()));
    private final AdminOidcSessionRevalidationFilter filter = new AdminOidcSessionRevalidationFilter(
            new AdminOidcSessionProperties(), service, Clock.fixed(NOW, ZoneOffset.UTC), contexts);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicOauthLogoutAssetsActuatorAndErrorRequestsSkipRevalidation() throws Exception {
        authenticate(AdminRole.EDITOR);
        for (String path : List.of("/", "/administer", "/assets/x", "/oauth2/authorization/authelia",
                "/login/oauth2/code/authelia", "/logout", "/actuator/health")) {
            filter.doFilter(request(path), response, chain);
        }
        MockHttpServletRequest error = request("/admin");
        error.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(error, response, chain);
        verifyNoInteractions(service);
        verify(chain, times(8)).doFilter(any(), any());
    }

    @Test
    void anonymousOtherAuthenticationAndMissingSessionContinueNormally() throws Exception {
        filter.doFilter(request("/admin"), response, chain);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test", "test", "ROLE_OWNER"));
        filter.doFilter(request("/admin"), response, chain);
        authenticate(AdminRole.OWNER);
        filter.doFilter(new MockHttpServletRequest("GET", "/admin"), response, chain);
        verifyNoInteractions(service);
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void youngTimestampSkipsWhileMissingWrongTypeAndExactBoundaryRevalidate() throws Exception {
        authenticate(AdminRole.EDITOR);
        var request = request("/admin");
        AdminOidcSessionState.markSuccessfulRevalidation(request, NOW.minusSeconds(299));
        filter.doFilter(request, response, chain);
        verifyNoInteractions(service);
        when(service.revalidate(any(), any(), any(), any())).thenReturn(user(AdminRole.OWNER));
        AdminOidcSessionState.markSuccessfulRevalidation(request, NOW.minusSeconds(300));
        filter.doFilter(request, response, chain);
        filter.doFilter(request("/admin"), response, chain);
        var malformed = request("/admin");
        malformed.getSession().setAttribute("dev.persefonia.security.oidc.last-successful-revalidation", "wrong-type");
        filter.doFilter(malformed, response, chain);
        verify(service, times(3)).revalidate(any(), any(), any(), any());
    }

    @Test
    void contextPathAndEncodedAdminPathsReceiveRevalidation() throws Exception {
        authenticate(AdminRole.OWNER);
        when(service.revalidate(any(), any(), any(), any())).thenReturn(user(AdminRole.OWNER));
        var request = request("/persefonia/admin/operations");
        request.setContextPath("/persefonia");
        filter.doFilter(request, response, chain);
        filter.doFilter(request("/%61dmin/operations"), response, chain);
        verify(service, times(2)).revalidate(any(), any(), any(), any());
    }

    @Test
    void newContextAndRolesAreExplicitlySavedAndVisibleOnSecondRequest() throws Exception {
        authenticate(AdminRole.EDITOR);
        var originalContext = SecurityContextHolder.getContext();
        var first = request("/admin/operations");
        when(service.revalidate(any(), any(), any(), any())).thenReturn(user(AdminRole.OWNER));
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(authority -> authority.getAuthority()).contains("ROLE_OWNER").doesNotContain("ROLE_EDITOR");
            return null;
        }).when(chain).doFilter(any(), any());
        filter.doFilter(first, response, chain);
        assertThat(SecurityContextHolder.getContext()).isNotSameAs(originalContext);
        assertThat(AdminOidcSessionState.lastSuccessfulRevalidation(first)).contains(NOW);
        verify(contexts).saveContext(any(), same(first), same(response));
        var session = (MockHttpSession) first.getSession();
        SecurityContextHolder.clearContext();
        var second = request("/admin/operations");
        second.setSession(session);
        SecurityContextHolder.setContext(contexts.loadDeferredContext(second).get());
        filter.doFilter(second, new MockHttpServletResponse(), chain);
        verify(service, times(1)).revalidate(any(), any(), any(), any());
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void everyFailureBlocksCurrentRequestWithLockedStatusAndSessionSemantics() throws Exception {
        for (AdminOidcSessionFailureReason reason : AdminOidcSessionFailureReason.values()) {
            authenticate(AdminRole.OWNER);
            var request = request("/admin");
            var session = (MockHttpSession) request.getSession();
            var context = SecurityContextHolder.getContext();
            new org.springframework.security.web.context.RequestAttributeSecurityContextRepository()
                    .saveContext(context, request, response);
            Instant previous = NOW.minusSeconds(301);
            AdminOidcSessionState.markSuccessfulRevalidation(request, previous);
            doThrow(new AdminOidcSessionRevalidationException(reason)).when(service).revalidate(any(), any(), any(), any());
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (reason == AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE) {
                assertThat(response.getStatus()).isEqualTo(503);
                assertThat(session.isInvalid()).isFalse();
                assertThat(AdminOidcSessionState.lastSuccessfulRevalidation(request)).contains(previous);
                assertThat(SecurityContextHolder.getContext()).isSameAs(context);
                // A later request remains due and retries.
                filter.doFilter(request, new MockHttpServletResponse(), chain);
            } else {
                assertThat(response.getStatus()).isEqualTo(reason == AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED ? 401 : 403);
                assertThat(session.isInvalid()).isTrue();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                assertThat(contexts.loadDeferredContext(request).get().getAuthentication()).isNull();
            }
        }
        verifyNoInteractions(chain);
        verify(contexts, times(3)).saveContext(any(), any(), any());
    }

    private static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setSession(new MockHttpSession());
        return request;
    }

    private static void authenticate(AdminRole role) {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        var user = user(role);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "authelia"));
    }

    private static PersefoniaOidcUser user(AdminRole role) {
        return new PersefoniaOidcUser(OidcTestFixtures.validUser(),
                AdminPrincipal.from(AdminOidcSessionRevalidationServiceTest.account(role)));
    }
}

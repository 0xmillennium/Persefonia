package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AdminOidcFreshUserServiceTest {
    private HttpServer server;
    private final java.util.concurrent.atomic.AtomicInteger redirects = new java.util.concurrent.atomic.AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>("{\"sub\":\"opaque-subject\",\"email\":\"admin@example.com\",\"groups\":[\"user\"]}");
    private int status = 200;
    private long delayMillis;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forcesUserInfoWithCurrentAccessTokenEvenWithoutDefaultUserInfoScopes() throws Exception {
        var user = load(new AdminOidcSessionProperties());
        assertThat(authorization.get()).isEqualTo("Bearer fake-current-access-token");
        assertThat(user.getUserInfo()).isNotNull();
        assertThat(new OidcClaimMapper().toAdminIdentityClaims(user).oidcGroups())
                .extracting(group -> group.value()).containsExactly("user");
    }

    @Test
    void matchingFreshSubjectAndAdminGroupAreAcceptedWithOptionalIdTokenProfileFallback() throws Exception {
        body.set("{\"sub\":\"opaque-subject\",\"groups\":[\"admin\"]}");

        var user = load(new AdminOidcSessionProperties());
        var claims = new OidcClaimMapper().toAdminIdentityClaims(user);

        assertThat(user.getUserInfo().getClaims()).containsEntry("sub", "opaque-subject")
                .doesNotContainKey("email");
        assertThat(claims.oidcSubject().value()).isEqualTo("opaque-subject");
        assertThat(claims.oidcGroups()).extracting(group -> group.value()).containsExactly("admin");
        assertThat(claims.email().value()).isEqualTo("admin@example.com");
    }

    @Test
    void missingFreshSubjectCannotBeSatisfiedByTheMergedIdTokenSubject() {
        var user = freshUser(Map.of("groups", List.of("admin")));
        assertThat(user.getIdToken().getSubject()).isEqualTo("opaque-subject");
        assertThat(new OidcClaimMapper().toAdminIdentityClaims(user).oidcSubject().value())
                .isEqualTo("opaque-subject");

        assertInvalidFreshUser(user);
    }

    @Test
    void rejectsNullEmptyBlankNonStringDifferentAndNormalizedFreshSubjects() {
        for (Object subject : new Object[] {null, "", " \t\n", 123, true, "different-subject",
                "OPAQUE-SUBJECT", " opaque-subject", "opaque-subject "}) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", subject);
            claims.put("groups", List.of("admin"));

            assertInvalidFreshUser(freshUser(claims));
        }
    }

    @Test
    void missingFreshGroupsCannotBeSatisfiedByIdTokenGroups() {
        assertInvalidFreshUser(freshUser(Map.of("sub", "opaque-subject")));
    }

    @Test
    void matchingFreshSubjectAndEmptyGroupsRemainStructurallyValid() throws Exception {
        body.set("{\"sub\":\"opaque-subject\",\"groups\":[]}");

        var user = load(new AdminOidcSessionProperties());

        assertThat(new OidcClaimMapper().toAdminIdentityClaims(user).oidcGroups()).isEmpty();
    }

    @Test
    void rejectsMissingFreshGroupsMissingSubjectMalformedJsonAndSubjectMismatch() throws Exception {
        startServer();
        for (String invalid : new String[] {"{}", "not-json", "{\"sub\":\"opaque-subject\"}",
                "{\"sub\":\"different-subject\",\"groups\":[\"admin\"]}"}) {
            body.set(invalid);
            assertFailure(new AdminOidcSessionProperties(), AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        }
        assertThatThrownBy(() -> new AdminOidcFreshUserService(request -> OidcTestFixtures.validUser())
                .loadFreshUser(registration(), token(), OidcTestFixtures.validUser().getIdToken()))
                .isInstanceOfSatisfying(AdminOidcSessionRevalidationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE));
    }

    @Test
    void timeoutConnectionFailureAndProvider5xxAreUnavailable() throws Exception {
        startServer();
        status = 503;
        assertFailure(new AdminOidcSessionProperties(), AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
        status = 200;
        delayMillis = 200;
        var properties = new AdminOidcSessionProperties();
        properties.setOidcReadTimeout(Duration.ofMillis(50));
        assertFailure(properties, AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
        server.stop(0);
        assertFailure(properties, AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
    }

    @Test
    void oauthAuthorizationRejectionExpiresAuthenticationAndRedirectIsNotFollowed() throws Exception {
        startServer();
        status = 401;
        assertFailure(new AdminOidcSessionProperties(), AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        status = 400;
        body.set("{\"error\":\"invalid_token\",\"error_description\":\"fake-current-access-token\"}");
        assertFailure(new AdminOidcSessionProperties(), AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        status = 302;
        assertFailure(new AdminOidcSessionProperties(), AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        assertThat(redirects.get()).isZero();
    }

    private void assertFailure(AdminOidcSessionProperties properties, AdminOidcSessionFailureReason reason) {
        assertThatThrownBy(() -> new AdminOidcFreshUserService(properties).loadFreshUser(registration(), token(),
                OidcTestFixtures.validUser().getIdToken()))
                .isInstanceOfSatisfying(AdminOidcSessionRevalidationException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason))
                .hasMessageNotContaining("fake-current-access-token").hasMessageNotContaining("opaque-subject")
                .hasMessageNotContaining("admin@example.com");
    }

    private static OidcUser freshUser(Map<String, Object> claims) {
        var stale = OidcTestFixtures.validUser();
        return new DefaultOidcUser(stale.getAuthorities(), stale.getIdToken(), new OidcUserInfo(claims));
    }

    private static void assertInvalidFreshUser(OidcUser user) {
        assertThatThrownBy(() -> new AdminOidcFreshUserService(request -> user).loadFreshUser(
                AdminOidcSessionRevalidationServiceTest.registration(), token(), OidcTestFixtures.validUser().getIdToken()))
                .isInstanceOfSatisfying(AdminOidcSessionRevalidationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE))
                .hasMessageNotContaining("opaque-subject").hasMessageNotContaining("admin@example.com")
                .hasMessageNotContaining("fake-id-token-value");
    }

    private org.springframework.security.oauth2.core.oidc.user.OidcUser load(AdminOidcSessionProperties properties) throws IOException {
        startServer();
        return new AdminOidcFreshUserService(properties).loadFreshUser(registration(), token(),
                OidcTestFixtures.validUser().getIdToken());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/userinfo", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (status == 302) {
                exchange.getResponseHeaders().set("Location", "/must-not-follow");
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.createContext("/must-not-follow", exchange -> {
            redirects.incrementAndGet();
            byte[] bytes = "{\"sub\":\"opaque-subject\",\"email\":\"admin@example.com\",\"groups\":[\"admin\"]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    private ClientRegistration registration() {
        return ClientRegistration.withClientRegistration(AdminOidcSessionRevalidationServiceTest.registration())
                .scope("openid")
                .userInfoUri("http://127.0.0.1:" + server.getAddress().getPort() + "/userinfo").build();
    }

    private static OAuth2AccessToken token() {
        Instant now = Instant.now();
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fake-current-access-token", now,
                now.plusSeconds(3600));
    }
}

package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminOidcSessionConfigurationTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdminOidcSessionConfiguration.class));

    @Test
    void noOauthClientInfrastructureWithoutRegistration() {
        contexts.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OAuth2AuthorizedClientRepository.class)
                    .doesNotHaveBean(OAuth2AuthorizedClientManager.class);
        });
    }

    @Test
    void rejectsInvalidSecurityTimingAtBeanCreation() {
        for (String property : new String[] {"revalidation-interval=0s", "revalidation-interval=-1s",
                "oidc-connect-timeout=0s", "oidc-read-timeout=31s"}) {
            contexts.withPropertyValues("persefonia.security.admin-session." + property)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void positiveDefaultsAndMaximumTimeoutAreValidatedWithoutFallback() {
        var properties = new AdminOidcSessionProperties();
        assertThat(properties.getRevalidationInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getOidcConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getOidcReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        properties.setOidcReadTimeout(Duration.ofSeconds(30));
        properties.afterPropertiesSet();
        properties.setOidcConnectTimeout(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realRefreshRotatesAndPersistsClientInSameSession() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            String form = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(form).contains("grant_type=refresh_token", "refresh_token=fake-original-refresh-token");
            byte[] bytes = ("{\"access_token\":\"fake-refreshed-access-token\",\"token_type\":\"Bearer\","
                    + "\"expires_in\":3600,\"refresh_token\":\"fake-rotated-refresh-token\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            var registration = org.springframework.security.oauth2.client.registration.ClientRegistration
                    .withClientRegistration(AdminOidcSessionRevalidationServiceTest.registration())
                    .tokenUri("http://127.0.0.1:" + server.getAddress().getPort() + "/token").build();
            var configuration = new AdminOidcSessionConfiguration();
            var repository = configuration.adminOAuth2AuthorizedClientRepository();
            var manager = configuration.adminOAuth2AuthorizedClientManager(
                    new InMemoryClientRegistrationRepository(registration), repository);
            var principal = new TestingAuthenticationToken("local-principal", "unused", "ROLE_ADMIN");
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            Instant now = Instant.now();
            repository.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, principal.getName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fake-expired-access-token",
                            now.minusSeconds(3600), now.minusSeconds(1)),
                    new OAuth2RefreshToken("fake-original-refresh-token", now.minusSeconds(3600))), principal, request, response);
            var authorize = OAuth2AuthorizeRequest.withClientRegistrationId("authelia").principal(principal)
                    .attributes(attributes -> {
                        attributes.put(jakarta.servlet.http.HttpServletRequest.class.getName(), request);
                        attributes.put(jakarta.servlet.http.HttpServletResponse.class.getName(), response);
                    }).build();
            OAuth2AuthorizedClient refreshed = manager.authorize(authorize);
            assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("fake-refreshed-access-token");
            assertThat(refreshed.getRefreshToken().getTokenValue()).isEqualTo("fake-rotated-refresh-token");
            assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient("authelia", principal, request)).isSameAs(refreshed);
            assertThat(manager.authorize(authorize)).isSameAs(refreshed);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredManagerRetainsCurrentClientAndRepositoryIsolatedBySession() {
        contexts.withBean(ClientRegistrationRepository.class, () -> new InMemoryClientRegistrationRepository(
                AdminOidcSessionRevalidationServiceTest.registration()))
                .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OAuth2AuthorizedClientRepository repository = context.getBean(OAuth2AuthorizedClientRepository.class);
                    assertThat(repository).isInstanceOf(HttpSessionOAuth2AuthorizedClientRepository.class);
                    OAuth2AuthorizedClientManager manager = context.getBean(OAuth2AuthorizedClientManager.class);
                    assertThat(manager).isInstanceOf(DefaultOAuth2AuthorizedClientManager.class);
                    var request = new MockHttpServletRequest();
                    var response = new MockHttpServletResponse();
                    var principal = new TestingAuthenticationToken("local-principal", "unused", "ROLE_ADMIN");
                    Instant now = Instant.now();
                    var client = new OAuth2AuthorizedClient(AdminOidcSessionRevalidationServiceTest.registration(),
                            principal.getName(), new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            "fake-access-token", now, now.plusSeconds(3600), Set.of("openid")),
                            new OAuth2RefreshToken("fake-refresh-token", now));
                    repository.saveAuthorizedClient(client, principal, request, response);
                    assertThat(manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("authelia")
                            .principal(principal).attributes(attributes -> attributes.putAll(Map.of(
                                    jakarta.servlet.http.HttpServletRequest.class.getName(), request,
                                    jakarta.servlet.http.HttpServletResponse.class.getName(), response))).build())).isSameAs(client);
                    assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient("authelia", principal, new MockHttpServletRequest())).isNull();
                });
    }
}

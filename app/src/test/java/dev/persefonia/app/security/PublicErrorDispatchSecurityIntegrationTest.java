package dev.persefonia.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.app.security.oidc.AdminOidcSessionRevalidationService;
import dev.persefonia.app.security.oidc.PersefoniaOidcUserService;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("public-error-dispatch-test")
@Import({TestPortfolioSettingsFallbackConfiguration.class,
        PublicErrorDispatchSecurityIntegrationTest.OidcTestConfiguration.class})
class PublicErrorDispatchSecurityIntegrationTest {
    @MockitoBean(name = "testPortfolioSettingsRepository")
    private SitePresentationSettingsRepository settings;

    @MockitoBean
    private AdminOidcSessionRevalidationService revalidation;

    @Autowired
    private ServletContext servletContext;

    @LocalServerPort
    private int port;

    @Test
    void embeddedServletContainerUsesOnlyCookieSessionTracking() {
        assertThat(servletContext.getEffectiveSessionTrackingModes()).isEqualTo(Set.of(SessionTrackingMode.COOKIE));
    }

    @Test
    void homepageApplicationFailureStaysAnUncacheableErrorWithoutAnOidcRedirect() throws Exception {
        when(settings.findCurrent()).thenReturn(Optional.empty());

        var response = get("/");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertPrivateNoStore(response);
    }

    @Test
    void directErrorRequestRemainsProtectedByTheOidcChain() throws Exception {
        var response = get("/error");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/oauth2/authorization/authelia").doesNotContain(";jsessionid=");
    }

    @Test
    void robotsRemainPublicAndAdminKeepsItsPrivateOidcRedirect() throws Exception {
        var robots = get("/robots.txt");
        assertThat(robots.statusCode()).isEqualTo(200);
        assertThat(robots.headers().firstValue("Location")).isEmpty();

        var admin = get("/admin");
        assertThat(admin.statusCode()).isEqualTo(302);
        assertThat(admin.headers().firstValue("Location").orElseThrow())
                .endsWith("/oauth2/authorization/authelia").doesNotContain(";jsessionid=");
        assertPrivateNoStore(admin);

        var authorization = get("/oauth2/authorization/authelia");
        assertThat(authorization.statusCode()).isEqualTo(302);
        assertThat(authorization.headers().firstValue("Location").orElseThrow())
                .startsWith("https://auth.example/authorize").doesNotContain(";jsessionid=");
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "text/html").timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertPrivateNoStore(HttpResponse<?> response) {
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow())
                .isEqualTo("no-store, no-cache, max-age=0, must-revalidate, private")
                .doesNotContain("public");
        assertThat(response.headers().firstValue("Pragma")).contains("no-cache");
        assertThat(ZonedDateTime.parse(response.headers().firstValue("Expires").orElseThrow(),
                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("public-error-dispatch-test")
    static class OidcTestConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("authelia")
                    .clientId("test-client")
                    .clientSecret("fake-test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile", "groups", "offline_access")
                    .authorizationUri("https://auth.example/authorize")
                    .tokenUri("https://auth.example/token")
                    .jwkSetUri("https://auth.example/jwks")
                    .userInfoUri("https://auth.example/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("Authelia")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        PersefoniaOidcUserService persefoniaOidcUserService() {
            return mock(PersefoniaOidcUserService.class);
        }
    }
}

package dev.persefonia.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.app.assets.ViteAssetResolver;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class SecurityPublicRouteTest {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private ViteAssetResolver viteAssetResolver;

    @Test
    void explicitPublicAllowlistRemainsAvailable() throws Exception {
        HttpResponse<String> home = get("/");

        assertEquals(200, home.statusCode());
        assertTrue(home.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(home.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));
        assertTrue(home.body().contains("Persefonia"));
        assertTrue(home.body().contains("/assets/"));

        assertEquals(200, get(viteAssetResolver.scriptPath(FRONTEND_ENTRY)).statusCode());
        for (String stylesheetPath : viteAssetResolver.stylesheetPaths(FRONTEND_ENTRY)) {
            assertEquals(200, get(stylesheetPath).statusCode());
        }
        assertEquals(200, get("/search").statusCode());
    }

    @Test
    void unsafeRequestsAreNotPublic() throws Exception {
        assertNotSuccessful(post("/"));
        assertNotSuccessful(post("/search"));
        assertNotSuccessful(post(viteAssetResolver.scriptPath(FRONTEND_ENTRY)));
    }

    @Test
    void absentSearchWildcardAndAliasFeedRoutesDoNotServePublicContent() throws Exception {
        for (String path : List.of(
                "/search/anything",
                "/sitemap.xml/anything",
                "/robots.txt/anything",
                "/feed.xml/anything",
                "/rss.xml",
                "/atom.xml")) {
            assertNotSuccessful(get(path));
        }

        assertEquals(200, get("/robots.txt").statusCode());
    }

    @Test
    void publicMediaVariantRouteAllowsAnonymousRequests() throws Exception {
        HttpResponse<String> response =
                get("/media/assets/00000000-0000-0000-0000-000000000000/variants/thumbnail");

        assertEquals(404, response.statusCode());
    }

    @Test
    void absentResumeAndGenericMediaRoutesDoNotServePublicContent() throws Exception {
        for (String path : List.of(
                "/resume",
                "/resume/en",
                "/media/assets/00000000-0000-0000-0000-000000000000",
                "/media/assets/00000000-0000-0000-0000-000000000000/download",
                "/media/assets/00000000-0000-0000-0000-000000000000/original")) {
            HttpResponse<String> response = get(path);

            assertNotSuccessful(response);
            assertFalse(response.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"));
        }
    }

    @Test
    void securityConfigurationDoesNotPermitAbsentResumeOrGenericMediaRoutes() throws Exception {
        String securityConfiguration = Files.readString(
                Path.of("src/main/java/dev/persefonia/app/security/SecurityConfiguration.java"));

        assertFalse(securityConfiguration.contains("\"/resume\""));
        assertFalse(securityConfiguration.contains("\"/resume/*\""));
        assertFalse(securityConfiguration.contains("\"/media/assets/*\""));
        assertFalse(securityConfiguration.contains("\"/media/assets/*/download\""));
        assertFalse(securityConfiguration.contains("\"/media/assets/*/original\""));
        assertTrue(securityConfiguration.contains("\"/media/assets/*/variants/*\""));
        assertTrue(securityConfiguration.contains("\"/search\""));
        assertFalse(securityConfiguration.contains("\"/search/**\""));
        assertTrue(securityConfiguration.contains("\"/sitemap.xml\""));
        assertTrue(securityConfiguration.contains("\"/robots.txt\""));
        assertFalse(securityConfiguration.contains("\"/sitemap.xml/**\""));
        assertFalse(securityConfiguration.contains("\"/robots.txt/**\""));
        assertTrue(securityConfiguration.contains("\"/feed.xml\""));
        assertFalse(securityConfiguration.contains("\"/feed.xml/**\""));
        assertFalse(securityConfiguration.contains("\"/rss.xml\""));
        assertFalse(securityConfiguration.contains("\"/atom.xml\""));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + applicationPort + path);
    }

    private void assertNotSuccessful(HttpResponse<?> response) {
        assertFalse(response.statusCode() >= 200 && response.statusCode() < 300);
    }
}

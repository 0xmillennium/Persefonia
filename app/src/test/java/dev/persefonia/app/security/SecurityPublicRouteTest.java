package dev.persefonia.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
    }

    @Test
    void unsafeRequestsAreNotPublic() throws Exception {
        assertNotSuccessful(post("/"));
        assertNotSuccessful(post(viteAssetResolver.scriptPath(FRONTEND_ENTRY)));
    }

    @Test
    void publicMediaVariantRouteAllowsAnonymousRequests() throws Exception {
        HttpResponse<String> response =
                get("/media/assets/00000000-0000-0000-0000-000000000000/variants/thumbnail");

        assertEquals(404, response.statusCode());
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

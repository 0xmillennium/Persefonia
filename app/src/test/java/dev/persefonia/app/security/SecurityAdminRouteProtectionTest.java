package dev.persefonia.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
class SecurityAdminRouteProtectionTest {
    private static final List<String> ADMIN_PATHS =
            List.of("/admin", "/admin/", "/admin/dashboard", "/admin/login", "/admin/assets");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int applicationPort;

    @Test
    void unauthenticatedAdminRequestsAreProtectedWithoutContentLeakage() throws Exception {
        for (String path : ADMIN_PATHS) {
            HttpResponse<String> response = get(path);
            String body = response.body().toLowerCase();

            assertFalse(response.statusCode() >= 200 && response.statusCode() < 300, path);
            assertTrue(response.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"), path);
            assertFalse(body.contains("persefonia admin shell"), path);
            assertFalse(body.contains("authentication will be added"), path);
            assertFalse(body.contains("admin shell"), path);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + applicationPort + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

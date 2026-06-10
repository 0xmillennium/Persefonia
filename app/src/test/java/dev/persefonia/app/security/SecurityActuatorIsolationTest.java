package dev.persefonia.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.server.address=127.0.0.1",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
class SecurityActuatorIsolationTest {
    private static final List<String> ALLOWED_MANAGEMENT_PATHS =
            List.of("/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/prometheus");
    private static final List<String> SENSITIVE_MANAGEMENT_PATHS =
            List.of("/actuator/env", "/actuator/beans", "/actuator/mappings", "/actuator/loggers");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void actuatorRemainsLimitedToTheSeparateManagementPort() throws Exception {
        for (String path : ALLOWED_MANAGEMENT_PATHS) {
            assertNotSuccessful(get(applicationPort, path), path);
            assertEquals(200, get(managementPort, path).statusCode(), path);
        }

        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertTrue(health.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));

        for (String path : SENSITIVE_MANAGEMENT_PATHS) {
            assertNotSuccessful(get(managementPort, path), path);
        }
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertNotSuccessful(HttpResponse<?> response, String path) {
        assertFalse(response.statusCode() >= 200 && response.statusCode() < 300, path);
    }
}

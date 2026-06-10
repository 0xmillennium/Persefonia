package dev.persefonia.app.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
class ActuatorExposureTest {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void exposesOnlySafeEndpointsOnTheManagementPort() throws Exception {
        assertNotSuccessful(applicationPort, "/actuator/health");

        assertStatus(managementPort, "/actuator/health", 200);
        assertStatus(managementPort, "/actuator/info", 200);
        assertStatus(managementPort, "/actuator/metrics", 200);
        assertStatus(managementPort, "/actuator/prometheus", 200);
        assertRequestIdHeader(managementPort, "/actuator/health");

        assertNotSuccessful(managementPort, "/actuator/env");
        assertNotSuccessful(managementPort, "/actuator/beans");
        assertNotSuccessful(managementPort, "/actuator/mappings");
        assertNotSuccessful(managementPort, "/actuator/loggers");
    }

    private void assertStatus(int port, String path, int expectedStatus) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(expectedStatus, response.statusCode(), path);
    }

    private void assertNotSuccessful(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertFalse(response.statusCode() >= 200 && response.statusCode() < 300, path);
    }

    private void assertRequestIdHeader(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertTrue(response.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));
    }
}

package dev.persefonia.app.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertStatus(applicationPort, "/actuator/health", 404);

        assertStatus(managementPort, "/actuator/health", 200);
        assertStatus(managementPort, "/actuator/info", 200);
        assertStatus(managementPort, "/actuator/metrics", 200);
        assertStatus(managementPort, "/actuator/prometheus", 200);
        assertRequestIdHeader(managementPort, "/actuator/health");

        assertStatus(managementPort, "/actuator/env", 404);
        assertStatus(managementPort, "/actuator/beans", 404);
        assertStatus(managementPort, "/actuator/mappings", 404);
        assertStatus(managementPort, "/actuator/loggers", 404);
    }

    private void assertStatus(int port, String path, int expectedStatus) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(expectedStatus, response.statusCode(), path);
    }

    private void assertRequestIdHeader(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertTrue(response.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));
    }
}

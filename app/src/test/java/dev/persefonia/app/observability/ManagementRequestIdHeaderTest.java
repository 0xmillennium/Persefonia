package dev.persefonia.app.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.server.address=127.0.0.1",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
class ManagementRequestIdHeaderTest {
    @LocalManagementPort
    private int managementPort;

    @Test
    void managementHealthResponseReceivesRequestIdHeader() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + managementPort + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<Void> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

        assertTrue(response.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));
    }
}

package dev.persefonia.app.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
class PrometheusEndpointTest {
    @LocalManagementPort
    private int managementPort;

    @Test
    void exposesScrapeablePrometheusOutput() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + managementPort + "/actuator/prometheus"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(response.statusCode() == 200);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
        assertTrue(response.body().contains("# HELP") || response.body().contains("# TYPE"));
        assertTrue(response.body().contains("jvm_")
                || response.body().contains("process_")
                || response.body().contains("application"));
        assertFalse(response.body().contains("super-secret"));
    }
}

package dev.persefonia.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
class SecurityHeadersTest {
    @LocalServerPort
    private int applicationPort;

    @Test
    void publicResponsesIncludeBaselineSecurityHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + applicationPort + "/"))
                .GET()
                .build();
        HttpResponse<Void> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertFalse(response.headers().firstValue("X-Frame-Options").orElse("").isBlank());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(""));
    }
}

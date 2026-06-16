package dev.persefonia.app.exposure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class PublicRouteExposureRegressionTest {
    @LocalServerPort
    private int applicationPort;

    @Test
    void homeIsTheCurrentPublicHtmlAllowlist() throws Exception {
        HttpResponse<String> response = ExposureTestSupport.get(applicationPort, "/");
        String body = response.body();

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.headers().firstValue("X-Request-Id").orElse("").matches("[A-Za-z0-9._-]+"));
        assertTrue(body.contains("Persefonia"));
        assertTrue(body.contains("0xmillennium"));
        assertTrue(body.contains("/assets/"));
        assertTrue(body.contains("<link rel=\"stylesheet\""));
        assertTrue(body.contains("<script type=\"module\""));
    }
}

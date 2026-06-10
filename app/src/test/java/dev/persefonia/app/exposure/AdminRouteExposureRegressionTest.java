package dev.persefonia.app.exposure;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
class AdminRouteExposureRegressionTest {
    private static final List<String> ADMIN_PATHS =
            List.of("/admin", "/admin/", "/admin/dashboard", "/admin/login", "/admin/assets");

    @LocalServerPort
    private int applicationPort;

    @Test
    void adminRoutesRemainUnexposed() throws Exception {
        for (String path : ADMIN_PATHS) {
            HttpResponse<String> response = ExposureTestSupport.get(applicationPort, path);
            String body = response.body().toLowerCase();

            assertFalse(response.statusCode() >= 200 && response.statusCode() < 300, path);
            assertFalse(body.contains("persefonia admin"), path);
            assertFalse(body.contains("logout"), path);
        }
    }
}

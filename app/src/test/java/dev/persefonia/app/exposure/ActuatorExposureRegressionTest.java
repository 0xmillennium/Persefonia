package dev.persefonia.app.exposure;

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
class ActuatorExposureRegressionTest {
    private static final String[] ALLOWED_ENDPOINTS = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/prometheus"
    };

    private static final String[] SENSITIVE_ENDPOINTS = {
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/beans",
            "/actuator/mappings",
            "/actuator/loggers",
            "/actuator/heapdump",
            "/actuator/threaddump",
            "/actuator/shutdown",
            "/actuator/httpexchanges",
            "/actuator/flyway"
    };

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void actuatorIsLimitedToTheManagementPortAllowlist() throws Exception {
        for (String path : ALLOWED_ENDPOINTS) {
            ExposureTestSupport.assertNotSuccessful(applicationPort, path);
            ExposureTestSupport.assertStatus(managementPort, path, 200);
        }

        for (String path : SENSITIVE_ENDPOINTS) {
            ExposureTestSupport.assertNotSuccessful(applicationPort, path);
            ExposureTestSupport.assertNotSuccessful(managementPort, path);
        }
    }
}

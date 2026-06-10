package dev.persefonia.app.exposure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import dev.persefonia.app.assets.ViteAssetResolver;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false"
        })
class StaticAssetExposureRegressionTest {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private static final List<String> FORBIDDEN_RESOURCES = List.of(
            "/.env",
            "/application.yml",
            "/application-local.yml",
            "/application-test.yml",
            "/db/migration/V1__create_schemas.sql",
            "/META-INF/MANIFEST.MF",
            "/META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports",
            "/secrets/postgres_password.txt",
            "/secrets/redis.conf");

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private ViteAssetResolver viteAssetResolver;

    @Test
    void onlyBuiltViteAssetsArePublicStaticResources() throws Exception {
        ExposureTestSupport.assertStatus(applicationPort, viteAssetResolver.scriptPath(FRONTEND_ENTRY), 200);
        for (String stylesheetPath : viteAssetResolver.stylesheetPaths(FRONTEND_ENTRY)) {
            ExposureTestSupport.assertStatus(applicationPort, stylesheetPath, 200);
        }

        for (String path : FORBIDDEN_RESOURCES) {
            ExposureTestSupport.assertNotSuccessful(applicationPort, path);
        }
    }
}

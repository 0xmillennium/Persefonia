package dev.persefonia.app.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Comparator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = SpringBootFlywayRuntimeIntegrationTest.DatabaseCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class SpringBootFlywayRuntimeIntegrationTest {
    private static final SharedPostgresTestServer.Database DATABASE = SharedPostgresTestServer.migrationDatabase();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    public static class DatabaseCleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            // afterTestClass runs in reverse order: DirtiesContext closes pooled connections first.
            return 1000;
        }

        @Override
        public void afterTestClass(TestContext testContext) {
            DATABASE.close();
        }
    }

    @Test
    void bootMigratesFreshPostgresSeedsSettingsAndServesThePublicHomepage() throws Exception {
        Flyway flyway = context.getBean(Flyway.class);
        assertThat(flyway).isNotNull();
        for (String table : new String[] {
                "operations.flyway_schema_history",
                "portfolio.site_presentation_settings",
                "portfolio.site_supported_languages"
        }) {
            assertThat(jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, table))
                    .as(table).isNotNull();
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'portfolio'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.site_presentation_settings", Integer.class))
                .isEqualTo(1);
        var seed = jdbc.queryForMap("""
                SELECT site_name, default_language, default_theme, featured_project_limit, latest_writing_limit
                FROM portfolio.site_presentation_settings
                """);
        assertThat(seed).containsEntry("site_name", "Persefonia")
                .containsEntry("default_language", "TR")
                .containsEntry("default_theme", "SYSTEM")
                .containsEntry("featured_project_limit", 3)
                .containsEntry("latest_writing_limit", 5);
        assertThat(jdbc.queryForList("SELECT language FROM portfolio.site_supported_languages", String.class))
                .containsExactlyInAnyOrder("EN", "TR");

        var info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(Arrays.stream(info.all()).filter(migration -> migration.getState().isFailed())).isEmpty();
        var latest = Arrays.stream(info.all())
                .filter(migration -> migration.getVersion() != null && migration.getState().isResolved())
                .max(Comparator.comparing(MigrationInfo::getVersion)).orElseThrow();
        assertThat(info.current().getVersion()).isEqualTo(latest.getVersion());

        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            assertThat(response.body()).contains("Persefonia");
            assertThat(response.headers().firstValue("Cache-Control"))
                    .contains(PublicContentResponseHeaders.PUBLIC_MUTABLE_CACHE_CONTROL);
        }
    }
}

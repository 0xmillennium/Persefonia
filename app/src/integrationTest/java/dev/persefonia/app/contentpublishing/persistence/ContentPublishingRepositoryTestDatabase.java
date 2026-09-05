package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
abstract class ContentPublishingRepositoryTestDatabase {
    static final SharedPostgresTestServer.Database POSTGRES = postgresContainer();

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database postgres = SharedPostgresTestServer.integrationDatabase();
        postgres.withDatabaseName("persefonia_repository_test");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }

    private static boolean migrated;

    @Autowired
    ContentItemRepository contentItems;

    @Autowired
    ContentRevisionRepository contentRevisions;

    @Autowired
    SeriesRepository seriesRepository;

    @Autowired
    NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void truncatePublishingTables() {
        migrateOnce();
        jdbc.execute("""
                TRUNCATE publishing.series_entries,
                    publishing.series,
                    publishing.translation_group_entries,
                    publishing.translation_groups,
                    publishing.content_revisions,
                    publishing.content_rendered_headings,
                    publishing.content_render_snapshots,
                    publishing.content_items
                RESTART IDENTITY CASCADE
                """);
    }

    private static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }        migrated = true;
    }
}

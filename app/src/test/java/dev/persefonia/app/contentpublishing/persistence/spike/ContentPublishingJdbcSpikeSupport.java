package dev.persefonia.app.contentpublishing.persistence.spike;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "spring.flyway.enabled=false"
})
abstract class ContentPublishingJdbcSpikeSupport {
    static final Instant NOW = Instant.parse("2026-06-11T09:00:00Z");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcAggregateTemplate aggregates;

    @Autowired
    NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    SpikeContentPersistenceAdapter adapter;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ContentPublishingJdbcSpikeSupport::publishingSchemaJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void createTestOnlySchema() {
        SpikeContentPublishingTestSchema.recreate(jdbc);
        adapter = new SpikeContentPersistenceAdapter(aggregates, namedJdbc, transactions, new SpikeContentItemMapper());
    }

    SpikeContentItem draftItem() {
        return new SpikeContentItem(
                UUID.randomUUID(),
                SpikeContentType.ARTICLE,
                SpikeContentStatus.DRAFT,
                SpikeContentVisibility.PRIVATE,
                SpikeLanguage.EN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                null,
                null);
    }

    SpikeContentItem publishedItem(String slug) {
        return new SpikeContentItem(
                UUID.randomUUID(),
                SpikeContentType.ARTICLE,
                SpikeContentStatus.PUBLISHED,
                SpikeContentVisibility.PUBLIC,
                SpikeLanguage.EN,
                slug,
                "Mapping Spike",
                "A persistence mapping spike.",
                "# Spike",
                "Mapping Spike",
                "Persistence mapping proof.",
                "/articles/" + slug,
                "Mapping Spike",
                "Persistence mapping proof.",
                UUID.randomUUID(),
                NOW.minusSeconds(60),
                null,
                NOW,
                NOW,
                null,
                null);
    }

    SpikeContentRenderSnapshot snapshot(String rendererVersion, List<SpikeRenderedHeading> headings) {
        String html = "v2".equals(rendererVersion)
                ? "<article><h1>Replacement</h1></article>"
                : "<article><h1>Spike</h1></article>";
        return new SpikeContentRenderSnapshot(
                html,
                NOW.plusSeconds(30),
                rendererVersion,
                4,
                true,
                headings);
    }

    SpikeRenderedHeading heading(String anchor, int level, int position) {
        return new SpikeRenderedHeading(UUID.randomUUID(), level, "Heading " + position, anchor, position);
    }

    SpikeContentRevision revision(UUID contentItemId, int revisionNumber, UUID adminRef) {
        return new SpikeContentRevision(
                UUID.randomUUID(),
                contentItemId,
                revisionNumber,
                SpikeRevisionType.AUTHOR_EDIT,
                "Mapping Spike",
                "mapping-spike",
                "A persistence mapping spike.",
                "# Spike",
                null,
                "Mapping Spike",
                "Persistence mapping proof.",
                "/articles/mapping-spike",
                "Mapping Spike",
                "Persistence mapping proof.",
                null,
                adminRef,
                NOW.plusSeconds(revisionNumber),
                null);
    }

    long countHeadings(UUID contentItemId) {
        return namedJdbc.queryForObject(
                "SELECT count(*) FROM publishing.content_rendered_headings WHERE content_item_id = :contentItemId",
                Map.of("contentItemId", contentItemId),
                Long.class);
    }

    long countHeadingAnchor(UUID contentItemId, String anchor) {
        return namedJdbc.queryForObject("""
                SELECT count(*)
                FROM publishing.content_rendered_headings
                WHERE content_item_id = :contentItemId AND anchor = :anchor
                """,
                Map.of("contentItemId", contentItemId, "anchor", anchor),
                Long.class);
    }

    void insertDuplicateHeadingAnchor(UUID contentItemId) {
        namedJdbc.update("""
                INSERT INTO publishing.content_rendered_headings
                    (id, content_item_id, level, text, anchor, position)
                VALUES
                    (:firstId, :contentItemId, 2, 'Duplicate first', 'duplicate-anchor', 10),
                    (:secondId, :contentItemId, 3, 'Duplicate second', 'duplicate-anchor', 11)
                """,
                Map.of(
                        "firstId", UUID.randomUUID(),
                        "secondId", UUID.randomUUID(),
                        "contentItemId", contentItemId));
    }

    void insertRawContentItem(Map<String, String> overrides) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("type", overrides.getOrDefault("type", "ARTICLE"))
                .addValue("status", overrides.getOrDefault("status", "PUBLISHED"))
                .addValue("visibility", overrides.getOrDefault("visibility", "PUBLIC"))
                .addValue("language", overrides.getOrDefault("language", "EN"))
                .addValue("slug", overrides.getOrDefault("slug", "raw-" + UUID.randomUUID()))
                .addValue("createdAt", Timestamp.from(NOW))
                .addValue("updatedAt", Timestamp.from(NOW))
                .addValue("version", 0L);
        namedJdbc.update("""
                INSERT INTO publishing.content_items
                    (id, type, status, visibility, language, slug, created_at, updated_at, version)
                VALUES
                    (:id, :type, :status, :visibility, :language, :slug, :createdAt, :updatedAt, :version)
                """, params);
    }

    private static String publishingSchemaJdbcUrl() {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=publishing";
    }
}

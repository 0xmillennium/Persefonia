package dev.persefonia.app.discovery.migration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

abstract class DiscoveryMigrationDatabase {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.migrationDatabase();
    private static final JdbcTemplate JDBC;
    private static final NamedParameterJdbcTemplate NAMED_JDBC;

    static {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JDBC = new JdbcTemplate(dataSource);
        NAMED_JDBC = new NamedParameterJdbcTemplate(dataSource);
        cleanMigrate();
    }

    @BeforeEach
    void truncateDiscovery() {
        JDBC.execute("TRUNCATE discovery.redirect_rules, discovery.discoverable_resources");
    }

    static void cleanMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas(
                        "operations", "iam", "taxonomy", "publishing", "portfolio", "media", "communication",
                        "discovery", "integrity", "insights", "audit", "portability")
                .createSchemas(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    JdbcTemplate jdbc() {
        return JDBC;
    }

    void insertResource(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("sourceContext", "CONTENT_PUBLISHING");
        values.put("sourceType", "CONTENT_ITEM");
        values.put("sourceEntityId", UUID.randomUUID());
        values.put("resourceType", "ARTICLE");
        values.put("routePurpose", "DETAIL");
        values.put("language", "EN");
        values.put("publicUrl", "/resource-" + UUID.randomUUID());
        values.put("canonicalUrl", "https://example.test/resource-" + UUID.randomUUID());
        values.put("title", "Title");
        values.put("summary", "Summary");
        values.put("indexingPolicy", "INDEX");
        values.put("searchEligibility", "ELIGIBLE");
        values.put("sitemapEligibility", "ELIGIBLE");
        values.put("feedEligibility", "ELIGIBLE");
        values.put("searchText", "Search text");
        values.put("createdAt", java.sql.Timestamp.from(java.time.Instant.parse("2026-06-14T10:00:00Z")));
        values.put("version", 0L);
        values.putAll(overrides);
        NAMED_JDBC.update("""
                INSERT INTO discovery.discoverable_resources (
                    id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
                    public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
                    sitemap_eligibility, feed_eligibility, search_text, created_at, version
                ) VALUES (
                    :id, :sourceContext, :sourceType, :sourceEntityId, :resourceType, :routePurpose, :language,
                    :publicUrl, :canonicalUrl, :title, :summary, :indexingPolicy, :searchEligibility,
                    :sitemapEligibility, :feedEligibility, :searchText, :createdAt, :version
                )
                """, new MapSqlParameterSource(values));
    }

    void insertRedirect(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("sourceUrl", "/old-" + UUID.randomUUID());
        values.put("targetUrl", "/new-" + UUID.randomUUID());
        values.put("statusCode", 308);
        values.put("reason", "MANUAL");
        values.put("sourceContext", null);
        values.put("sourceType", null);
        values.put("sourceEntityId", null);
        values.put("active", true);
        values.put("createdAt", java.sql.Timestamp.from(java.time.Instant.parse("2026-06-14T10:00:00Z")));
        values.put("updatedAt", java.sql.Timestamp.from(java.time.Instant.parse("2026-06-14T10:00:00Z")));
        values.put("version", 0L);
        values.putAll(overrides);
        NAMED_JDBC.update("""
                INSERT INTO discovery.redirect_rules (
                    id, source_url, target_url, status_code, reason, source_context, source_type, source_entity_id,
                    active, created_at, updated_at, version
                ) VALUES (
                    :id, :sourceUrl, :targetUrl, :statusCode, :reason, :sourceContext, :sourceType, :sourceEntityId,
                    :active, :createdAt, :updatedAt, :version
                )
                """, new MapSqlParameterSource(values));
    }
}

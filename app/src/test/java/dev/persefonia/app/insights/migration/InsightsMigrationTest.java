package dev.persefonia.app.insights.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class InsightsMigrationTest {
    private static final List<String> FORBIDDEN_INSIGHTS_COLUMNS = List.of(
            "raw_ip",
            "ip_address",
            "hashed_ip",
            "ip_hash",
            "user_agent",
            "user_agent_summary",
            "user_agent_hash",
            "session_id",
            "visitor_id",
            "tracking_cookie_id",
            "country_code",
            "public_path",
            "search_term",
            "cv_asset_id",
            "not_found_path");
    private static final List<String> FORBIDDEN_RAW_EVENT_TABLES = List.of(
            "analytics_events",
            "raw_events",
            "page_view_events",
            "search_events");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearInsightsFoundationTables() {
        jdbc.execute("TRUNCATE insights.analytics_dimensions CASCADE");
    }

    @Test
    void insightsAggregateTablesExist() {
        assertThat(insightsTables()).contains("analytics_dimensions", "analytics_counters");
    }

    @Test
    void invalidEventTypeAndPeriodGranularityAreRejected() {
        assertThatThrownBy(() -> insertDimension(UUID.randomUUID(), "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID dimensionId = UUID.randomUUID();
        insertDimension(dimensionId, "PUBLIC_PAGE_VIEW");
        assertThatThrownBy(() -> insertCounter(UUID.randomUUID(), "PUBLIC_PAGE_VIEW", "HOUR", dimensionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateDimensionUniquenessTreatsNullsAsNotDistinct() {
        insertDimension(UUID.randomUUID(), "PUBLIC_PAGE_VIEW");

        assertThatThrownBy(() -> insertDimension(UUID.randomUUID(), "PUBLIC_PAGE_VIEW"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateCounterUniquenessSupportsFutureAtomicUpsert() {
        UUID dimensionId = UUID.randomUUID();
        insertDimension(dimensionId, "PUBLIC_SEARCH_SUBMITTED");
        insertCounter(UUID.randomUUID(), "PUBLIC_SEARCH_SUBMITTED", "DAY", dimensionId);

        assertThatThrownBy(() -> insertCounter(UUID.randomUUID(), "PUBLIC_SEARCH_SUBMITTED", "DAY", dimensionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rawEventTablesAreAbsentAndNoSeedDataExists() {
        assertThat(insightsTables()).doesNotContainAnyElementsOf(FORBIDDEN_RAW_EVENT_TABLES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insights.analytics_dimensions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insights.analytics_counters", Integer.class)).isZero();
    }

    @Test
    void aggregateInsightsTablesHaveNoForbiddenIdentityColumnsOrCountryCode() {
        assertThat(columns("analytics_dimensions")).doesNotContainAnyElementsOf(FORBIDDEN_INSIGHTS_COLUMNS);
        assertThat(columns("analytics_counters")).doesNotContainAnyElementsOf(FORBIDDEN_INSIGHTS_COLUMNS);
    }

    @Test
    void insightsDimensionsAreBoundedByMetricAndSurfaceOnly() {
        assertThat(columns("analytics_dimensions")).containsExactlyInAnyOrder("id", "metric", "surface", "created_at");
    }

    private static List<String> insightsTables() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'insights'
                """, String.class);
    }

    private static List<String> columns(String tableName) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'insights' AND table_name = ?
                """, String.class, tableName);
    }

    private static void insertDimension(UUID id, String eventType) {
        jdbc.update("""
                INSERT INTO insights.analytics_dimensions (
                    id, metric, surface, created_at
                ) VALUES (?, ?, 'HOME', ?)
                """, id, eventType, Timestamp.from(Instant.parse("2026-06-25T10:00:00Z")));
    }

    private static void insertCounter(UUID id, String eventType, String granularity, UUID dimensionId) {
        Instant now = Instant.parse("2026-06-25T10:01:00Z");
        jdbc.update("""
                INSERT INTO insights.analytics_counters (
                    id, metric, period_start, period_granularity, dimension_id, count,
                    first_seen_at, last_seen_at, version
                ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, 0)
                """,
                id,
                eventType,
                Date.valueOf(LocalDate.parse("2026-06-25")),
                granularity,
                dimensionId,
                Timestamp.from(now),
                Timestamp.from(now));
    }
}

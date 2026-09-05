package dev.persefonia.app.insights.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class JdbcInsightsCounterRepositoryAdapterTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static JdbcTemplate jdbc;
    private static JdbcInsightsCounterRepositoryAdapter adapter;

    @BeforeAll
    static void migrate() {        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("namedParameterJdbcTemplate", new NamedParameterJdbcTemplate(dataSource));
        adapter = new JdbcInsightsCounterRepositoryAdapter(
                beans.getBeanProvider(NamedParameterJdbcTemplate.class),
                Clock.fixed(Instant.parse("2026-06-25T10:15:00Z"), ZoneOffset.UTC));
    }

    @AfterAll
    static void stopDatabase() {    }

    @BeforeEach
    void clearInsightsTables() {
        jdbc.execute("TRUNCATE insights.analytics_dimensions CASCADE");
    }

    @Test
    void firstIncrementPersistsDimensionAndCounter() {
        adapter.increment(command(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, "2026-06-25"));

        assertThat(dimensions()).containsExactly(Map.of(
                "metric", "PUBLIC_PAGE_VIEW",
                "surface", "HOME"));
        assertThat(counterCounts()).containsExactly(1L);
    }

    @Test
    void repeatIncrementReusesDimensionAndAtomicallyIncrementsCounter() {
        adapter.increment(command(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH, "2026-06-25"));
        adapter.increment(command(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH, "2026-06-25"));

        assertThat(dimensions()).hasSize(1);
        assertThat(counterCounts()).containsExactly(2L);
    }

    @Test
    void differentDateMetricOrSurfaceCreatesSeparateCounters() {
        adapter.increment(command(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, "2026-06-25"));
        adapter.increment(command(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, "2026-06-26"));
        adapter.increment(command(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.CONTACT, "2026-06-25"));
        adapter.increment(command(InsightMetric.PUBLIC_NOT_FOUND, InsightSurface.NOT_FOUND, "2026-06-25"));

        assertThat(dimensions()).hasSize(3);
        assertThat(counterCounts()).containsExactly(1L, 1L, 1L, 1L);
    }

    @Test
    void rawEventTablesAndForbiddenColumnsAreAbsent() {
        assertThat(tables()).doesNotContain("analytics_events", "insights_events", "raw_events", "page_view_events");
        assertThat(columns("analytics_dimensions")).doesNotContain(
                "raw_ip",
                "hashed_ip",
                "user_agent",
                "referrer",
                "country_code",
                "visitor_id",
                "session_id",
                "search_term",
                "public_path",
                "not_found_path");
    }

    private static RecordInsightObservationCommand command(
            InsightMetric metric,
            InsightSurface surface,
            String date) {
        return new RecordInsightObservationCommand(metric, surface, LocalDate.parse(date), 1);
    }

    private static List<Map<String, Object>> dimensions() {
        return jdbc.queryForList("""
                SELECT metric, surface
                FROM insights.analytics_dimensions
                ORDER BY metric, surface
                """);
    }

    private static List<Long> counterCounts() {
        return jdbc.queryForList("""
                SELECT count
                FROM insights.analytics_counters
                ORDER BY metric, period_start, dimension_id
                """, Long.class);
    }

    private static List<String> tables() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'insights'
                """, String.class);
    }

    private static List<String> columns(String tableName) {
        return jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'insights' AND table_name = ?
                """, String.class, tableName);
    }
}

package dev.persefonia.app.insights.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.application.query.AdminAnalyticsMetricRow;
import dev.persefonia.insights.application.query.AdminAnalyticsSummary;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class JdbcAdminAnalyticsSummaryQueryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-25T10:15:00Z"), ZoneOffset.UTC);
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static JdbcTemplate jdbc;
    private static JdbcInsightsCounterRepositoryAdapter counters;
    private static JdbcAdminAnalyticsSummaryQueryService summaries;

    @BeforeAll
    static void migrate() {        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("namedParameterJdbcTemplate", new NamedParameterJdbcTemplate(dataSource));
        var jdbcProvider = beans.getBeanProvider(NamedParameterJdbcTemplate.class);
        counters = new JdbcInsightsCounterRepositoryAdapter(jdbcProvider, CLOCK);
        summaries = new JdbcAdminAnalyticsSummaryQueryService(jdbcProvider, CLOCK);
    }

    @AfterAll
    static void stopDatabase() {    }

    @Test
    void summaryIsEmptyWhenNoCountersExist() {
        AdminAnalyticsSummary summary = summaries.summarize();

        assertThat(summary.isEmpty()).isTrue();
        assertThat(summary.rows()).isEmpty();
        assertThat(summary.range().today()).isEqualTo(LocalDate.parse("2026-06-25"));
    }

    @Test
    void summaryAggregatesWindowedTotalsPerMetricAndSurface() {
        record(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, "2026-06-25");
        record(InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, "2026-06-20");
        record(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH, "2026-06-01");
        record(InsightMetric.PUBLIC_NOT_FOUND, InsightSurface.NOT_FOUND, "2026-01-01");

        AdminAnalyticsSummary summary = summaries.summarize();

        assertThat(summary.rows()).containsExactly(
                new AdminAnalyticsMetricRow(
                        InsightMetric.PUBLIC_NOT_FOUND, InsightSurface.NOT_FOUND, 0, 0, 0, 1),
                new AdminAnalyticsMetricRow(
                        InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, 1, 2, 2, 2),
                new AdminAnalyticsMetricRow(
                        InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH, 0, 0, 1, 1));
        assertThat(summary.totalTodayCount()).isEqualTo(1);
        assertThat(summary.totalAllTimeCount()).isEqualTo(4);
    }

    private static void record(InsightMetric metric, InsightSurface surface, String date) {
        counters.increment(new RecordInsightObservationCommand(metric, surface, LocalDate.parse(date), 1));
    }
}

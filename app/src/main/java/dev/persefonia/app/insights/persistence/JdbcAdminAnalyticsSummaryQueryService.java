package dev.persefonia.app.insights.persistence;

import dev.persefonia.insights.application.query.AdminAnalyticsDateRange;
import dev.persefonia.insights.application.query.AdminAnalyticsMetricRow;
import dev.persefonia.insights.application.query.AdminAnalyticsSummary;
import dev.persefonia.insights.application.query.AdminAnalyticsSummaryQueryService;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the privacy-safe aggregate Insights counters and projects them into the
 * admin analytics summary. It joins counters to their bounded (metric, surface)
 * dimension and sums daily totals across fixed reporting windows. It never reads
 * raw events, paths, search terms, or any visitor metadata.
 */
@Repository
public class JdbcAdminAnalyticsSummaryQueryService implements AdminAnalyticsSummaryQueryService {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final Clock clock;

    JdbcAdminAnalyticsSummaryQueryService(ObjectProvider<NamedParameterJdbcTemplate> jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsSummary summarize() {
        AdminAnalyticsDateRange range = AdminAnalyticsDateRange.endingOn(LocalDate.now(clock));
        List<AdminAnalyticsMetricRow> rows = jdbc().query("""
                SELECT
                    d.metric AS metric,
                    d.surface AS surface,
                    COALESCE(SUM(CASE WHEN c.period_start = :today THEN c.count ELSE 0 END), 0) AS today_count,
                    COALESCE(SUM(CASE WHEN c.period_start >= :sevenStart THEN c.count ELSE 0 END), 0) AS seven_count,
                    COALESCE(SUM(CASE WHEN c.period_start >= :thirtyStart THEN c.count ELSE 0 END), 0) AS thirty_count,
                    COALESCE(SUM(c.count), 0) AS all_time_count
                FROM insights.analytics_dimensions d
                LEFT JOIN insights.analytics_counters c ON c.dimension_id = d.id
                GROUP BY d.metric, d.surface
                ORDER BY d.metric, d.surface
                """, Map.of(
                "today", Date.valueOf(range.today()),
                "sevenStart", Date.valueOf(range.lastSevenDaysStart()),
                "thirtyStart", Date.valueOf(range.lastThirtyDaysStart())),
                (resultSet, rowNumber) -> new AdminAnalyticsMetricRow(
                        InsightMetric.valueOf(resultSet.getString("metric")),
                        InsightSurface.valueOf(resultSet.getString("surface")),
                        resultSet.getLong("today_count"),
                        resultSet.getLong("seven_count"),
                        resultSet.getLong("thirty_count"),
                        resultSet.getLong("all_time_count")));
        return new AdminAnalyticsSummary(range, rows);
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("JDBC is unavailable");
        }
        return available;
    }
}

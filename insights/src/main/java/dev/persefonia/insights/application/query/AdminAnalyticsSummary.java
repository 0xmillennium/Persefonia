package dev.persefonia.insights.application.query;

import java.util.List;
import java.util.Objects;

/**
 * Read-only aggregate analytics summary for the admin surface. It exposes only
 * bounded (metric, surface) rows with windowed totals; it carries no raw events
 * and no visitor, request, or per-resource metadata.
 */
public record AdminAnalyticsSummary(
        AdminAnalyticsDateRange range,
        List<AdminAnalyticsMetricRow> rows) {
    public AdminAnalyticsSummary {
        Objects.requireNonNull(range, "range");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public long totalTodayCount() {
        return rows.stream().mapToLong(AdminAnalyticsMetricRow::todayCount).sum();
    }

    public long totalLastSevenDaysCount() {
        return rows.stream().mapToLong(AdminAnalyticsMetricRow::lastSevenDaysCount).sum();
    }

    public long totalLastThirtyDaysCount() {
        return rows.stream().mapToLong(AdminAnalyticsMetricRow::lastThirtyDaysCount).sum();
    }

    public long totalAllTimeCount() {
        return rows.stream().mapToLong(AdminAnalyticsMetricRow::allTimeCount).sum();
    }
}

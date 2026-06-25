package dev.persefonia.insights.application.query;

import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.util.Objects;

/**
 * One row of the aggregate analytics summary: a bounded (metric, surface) pair
 * with privacy-safe totals across the reporting windows. It never carries paths,
 * search terms, visitor identity, or any per-resource detail.
 */
public record AdminAnalyticsMetricRow(
        InsightMetric metric,
        InsightSurface surface,
        long todayCount,
        long lastSevenDaysCount,
        long lastThirtyDaysCount,
        long allTimeCount) {
    public AdminAnalyticsMetricRow {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(surface, "surface");
        if (todayCount < 0 || lastSevenDaysCount < 0 || lastThirtyDaysCount < 0 || allTimeCount < 0) {
            throw new IllegalArgumentException("aggregate counts must not be negative");
        }
    }
}

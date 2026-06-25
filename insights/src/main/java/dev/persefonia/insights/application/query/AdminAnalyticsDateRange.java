package dev.persefonia.insights.application.query;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Reference date and the inclusive start dates of the bounded reporting windows
 * used by the aggregate admin analytics summary. No window is open-ended and no
 * arbitrary date filtering is exposed.
 */
public record AdminAnalyticsDateRange(
        LocalDate today,
        LocalDate lastSevenDaysStart,
        LocalDate lastThirtyDaysStart) {
    public AdminAnalyticsDateRange {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(lastSevenDaysStart, "lastSevenDaysStart");
        Objects.requireNonNull(lastThirtyDaysStart, "lastThirtyDaysStart");
    }

    public static AdminAnalyticsDateRange endingOn(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return new AdminAnalyticsDateRange(
                today,
                today.minusDays(6),
                today.minusDays(29));
    }
}

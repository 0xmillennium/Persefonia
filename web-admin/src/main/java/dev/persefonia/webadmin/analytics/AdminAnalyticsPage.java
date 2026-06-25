package dev.persefonia.webadmin.analytics;

import dev.persefonia.insights.application.query.AdminAnalyticsSummary;
import java.util.Objects;

public record AdminAnalyticsPage(
        AdminAnalyticsPageChrome chrome,
        AdminAnalyticsSummary summary) {
    public AdminAnalyticsPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(summary, "summary");
    }
}

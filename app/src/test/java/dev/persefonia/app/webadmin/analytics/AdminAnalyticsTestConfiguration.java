package dev.persefonia.app.webadmin.analytics;

import dev.persefonia.insights.application.query.AdminAnalyticsDateRange;
import dev.persefonia.insights.application.query.AdminAnalyticsMetricRow;
import dev.persefonia.insights.application.query.AdminAnalyticsSummary;
import dev.persefonia.insights.application.query.AdminAnalyticsSummaryQueryService;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightSurface;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-analytics-mvc-test")
class AdminAnalyticsTestConfiguration {
    @Bean
    @Primary
    AdminAnalyticsSummaryQueryService adminAnalyticsSummaryQueryService() {
        return () -> new AdminAnalyticsSummary(
                AdminAnalyticsDateRange.endingOn(LocalDate.parse("2026-06-25")),
                List.of(new AdminAnalyticsMetricRow(
                        InsightMetric.PUBLIC_PAGE_VIEW, InsightSurface.HOME, 1, 2, 3, 4)));
    }
}

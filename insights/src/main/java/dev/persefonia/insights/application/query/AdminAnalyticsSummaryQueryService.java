package dev.persefonia.insights.application.query;

/**
 * Read-only port that produces the aggregate admin analytics summary from the
 * privacy-safe Insights counters. Implementations must read from the bounded
 * (metric, surface) aggregate counters only and never from raw events.
 */
public interface AdminAnalyticsSummaryQueryService {
    AdminAnalyticsSummary summarize();
}

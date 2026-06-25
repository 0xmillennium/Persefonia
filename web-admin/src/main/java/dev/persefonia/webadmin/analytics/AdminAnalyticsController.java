package dev.persefonia.webadmin.analytics;

import dev.persefonia.insights.application.query.AdminAnalyticsSummary;
import dev.persefonia.insights.application.query.AdminAnalyticsSummaryQueryService;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Read-only, OWNER-protected aggregate analytics summary. It renders only the
 * privacy-safe (metric, surface) counters and exposes no mutation endpoint.
 */
@Controller
public final class AdminAnalyticsController {
    private final AdminAnalyticsSummaryQueryService summaries;
    private final AdminAnalyticsPageChromeFactory chrome;

    public AdminAnalyticsController(
            AdminAnalyticsSummaryQueryService summaries,
            AdminAnalyticsPageChromeFactory chrome) {
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/analytics")
    public String summary(Authentication authentication, CsrfToken csrfToken, Model model) {
        AdminAnalyticsSummary summary = summaries.summarize();
        model.addAttribute("page", new AdminAnalyticsPage(
                chrome.create(authentication, csrfToken),
                summary));
        return "admin/analytics/index";
    }
}

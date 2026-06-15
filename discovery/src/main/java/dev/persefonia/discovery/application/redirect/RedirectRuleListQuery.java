package dev.persefonia.discovery.application.redirect;

import java.util.Objects;

public record RedirectRuleListQuery(
        RedirectRuleStatusFilter status,
        int limit) {
    public static final int DEFAULT_LIMIT = 100;

    public RedirectRuleListQuery {
        Objects.requireNonNull(status, "status");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static RedirectRuleListQuery latestAll() {
        return new RedirectRuleListQuery(RedirectRuleStatusFilter.ALL, DEFAULT_LIMIT);
    }
}

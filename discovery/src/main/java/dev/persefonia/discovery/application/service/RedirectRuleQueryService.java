package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.port.ListRedirectRulesPort;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleListResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleSummary;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import java.util.Objects;

public final class RedirectRuleQueryService implements ListRedirectRulesPort {
    private final RedirectRuleRepository repository;

    public RedirectRuleQueryService(RedirectRuleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public RedirectRuleListResult list(RedirectRuleListQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return new RedirectRuleListResult(repository.list(query.status(), query.limit()).stream()
                .map(RedirectRuleQueryService::summary)
                .toList());
    }

    private static RedirectRuleSummary summary(RedirectRule rule) {
        var sourceRef = rule.sourceRef().orElse(null);
        return new RedirectRuleSummary(
                rule.id(),
                rule.sourceUrl(),
                rule.targetUrl(),
                rule.statusCode(),
                rule.reason(),
                rule.active(),
                sourceRef == null ? null : sourceRef.sourceContext(),
                sourceRef == null ? null : sourceRef.sourceType(),
                sourceRef == null ? null : sourceRef.sourceEntityId(),
                rule.createdAt(),
                rule.updatedAt(),
                rule.version());
    }
}

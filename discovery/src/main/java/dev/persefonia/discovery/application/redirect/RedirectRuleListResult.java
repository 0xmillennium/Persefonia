package dev.persefonia.discovery.application.redirect;

import java.util.List;
import java.util.Objects;

public record RedirectRuleListResult(List<RedirectRuleSummary> rules) {
    public RedirectRuleListResult {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }
}

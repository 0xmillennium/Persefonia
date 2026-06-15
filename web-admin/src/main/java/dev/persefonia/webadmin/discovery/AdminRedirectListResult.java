package dev.persefonia.webadmin.discovery;

import java.util.List;
import java.util.Objects;

public record AdminRedirectListResult(List<AdminRedirectRuleView> rules) {
    public AdminRedirectListResult {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }
}

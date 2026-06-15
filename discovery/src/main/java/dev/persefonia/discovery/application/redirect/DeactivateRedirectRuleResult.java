package dev.persefonia.discovery.application.redirect;

public sealed interface DeactivateRedirectRuleResult
        permits DeactivateRedirectRuleResult.Deactivated,
                DeactivateRedirectRuleResult.AlreadyInactive,
                DeactivateRedirectRuleResult.NotFound {

    record Deactivated() implements DeactivateRedirectRuleResult {
    }

    record AlreadyInactive() implements DeactivateRedirectRuleResult {
    }

    record NotFound() implements DeactivateRedirectRuleResult {
    }
}

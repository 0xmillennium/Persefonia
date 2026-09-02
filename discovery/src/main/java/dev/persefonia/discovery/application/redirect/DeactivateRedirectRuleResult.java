package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.Objects;

public sealed interface DeactivateRedirectRuleResult
        permits DeactivateRedirectRuleResult.Deactivated,
                DeactivateRedirectRuleResult.AlreadyInactive,
                DeactivateRedirectRuleResult.NotFound {

    record Deactivated(RedirectRuleChangeSummary redirect) implements DeactivateRedirectRuleResult {
        public Deactivated {
            Objects.requireNonNull(redirect, "redirect");
        }
    }

    record AlreadyInactive(RedirectRuleChangeSummary redirect) implements DeactivateRedirectRuleResult {
        public AlreadyInactive {
            Objects.requireNonNull(redirect, "redirect");
        }
    }

    record NotFound(RedirectRuleId redirectRuleId) implements DeactivateRedirectRuleResult {
        public NotFound {
            Objects.requireNonNull(redirectRuleId, "redirectRuleId");
        }
    }
}

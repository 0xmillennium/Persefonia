package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.Objects;

public record DeactivateRedirectRuleCommand(RedirectRuleId redirectRuleId) {
    public DeactivateRedirectRuleCommand {
        Objects.requireNonNull(redirectRuleId, "redirectRuleId");
    }
}

package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.Objects;

public record DeactivateManualRedirectCommand(
        AdminRedirectCommandActor actor,
        RedirectRuleId redirectRuleId) {
    public DeactivateManualRedirectCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(redirectRuleId, "redirectRuleId");
    }
}

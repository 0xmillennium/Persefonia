package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;

public interface AdminRedirectCommandGateway {
    RedirectRuleCreationResult create(CreateManualRedirectCommand command);

    DeactivateRedirectRuleResult deactivate(DeactivateManualRedirectCommand command);
}

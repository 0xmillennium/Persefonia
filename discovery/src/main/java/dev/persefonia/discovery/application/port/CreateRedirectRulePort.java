package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;

public interface CreateRedirectRulePort {
    RedirectRuleCreationResult create(CreateRedirectRuleCommand command);
}

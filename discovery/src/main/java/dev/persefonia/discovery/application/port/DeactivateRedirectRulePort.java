package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;

public interface DeactivateRedirectRulePort {
    DeactivateRedirectRuleResult deactivate(DeactivateRedirectRuleCommand command);
}

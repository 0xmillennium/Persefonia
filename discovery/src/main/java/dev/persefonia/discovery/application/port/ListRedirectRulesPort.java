package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleListResult;

public interface ListRedirectRulesPort {
    RedirectRuleListResult list(RedirectRuleListQuery query);
}

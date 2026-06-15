package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.port.ListRedirectRulesPort;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleListResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleSummary;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-redirect-mvc-test")
class AdminRedirectTestConfiguration {
    @Bean
    AdminRedirectTestPorts adminRedirectTestPorts() {
        return new AdminRedirectTestPorts();
    }

    @Bean
    @Primary
    ListRedirectRulesPort adminRedirectTestListRedirectRulesPort(AdminRedirectTestPorts ports) {
        return ports::list;
    }

    @Bean
    @Primary
    CreateRedirectRulePort adminRedirectTestCreateRedirectRulePort(AdminRedirectTestPorts ports) {
        return ports::create;
    }

    @Bean
    @Primary
    DeactivateRedirectRulePort adminRedirectTestDeactivateRedirectRulePort(AdminRedirectTestPorts ports) {
        return ports::deactivate;
    }

    static final class AdminRedirectTestPorts {
        private final List<RedirectRuleSummary> rules = new ArrayList<>();
        private RedirectRuleCreationResult createResult = new RedirectRuleCreationResult.Created();
        private DeactivateRedirectRuleResult deactivateResult = new DeactivateRedirectRuleResult.Deactivated();
        private CreateRedirectRuleCommand lastCreateCommand;
        private DeactivateRedirectRuleCommand lastDeactivateCommand;

        RedirectRuleListResult list(RedirectRuleListQuery query) {
            return new RedirectRuleListResult(rules);
        }

        RedirectRuleCreationResult create(CreateRedirectRuleCommand command) {
            lastCreateCommand = command;
            return createResult;
        }

        DeactivateRedirectRuleResult deactivate(DeactivateRedirectRuleCommand command) {
            lastDeactivateCommand = command;
            return deactivateResult;
        }

        void reset() {
            rules.clear();
            createResult = new RedirectRuleCreationResult.Created();
            deactivateResult = new DeactivateRedirectRuleResult.Deactivated();
            lastCreateCommand = null;
            lastDeactivateCommand = null;
        }

        void addRule(RedirectRuleSummary rule) {
            rules.add(rule);
        }

        void rejectCreate(RedirectRuleCreationResult.Reason reason) {
            createResult = new RedirectRuleCreationResult.Rejected(reason);
        }

        CreateRedirectRuleCommand lastCreateCommand() {
            return lastCreateCommand;
        }

        DeactivateRedirectRuleCommand lastDeactivateCommand() {
            return lastDeactivateCommand;
        }

        static RedirectRuleSummary activeRule(UUID id) {
            Instant now = Instant.parse("2026-06-14T08:00:00Z");
            return new RedirectRuleSummary(
                    new RedirectRuleId(id),
                    new PublicUrl("/tr/articles/old"),
                    new PublicUrl("/tr/articles/new"),
                    RedirectStatusCode.MOVED_PERMANENTLY_301,
                    RedirectReason.MANUAL,
                    true,
                    null,
                    null,
                    null,
                    now,
                    now,
                    Version.initial());
        }
    }
}

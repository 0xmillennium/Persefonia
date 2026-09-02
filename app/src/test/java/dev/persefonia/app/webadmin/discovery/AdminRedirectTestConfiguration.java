package dev.persefonia.app.webadmin.discovery;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.port.ListRedirectRulesPort;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleListResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleSummary;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
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
        return ports::createCore;
    }

    @Bean
    @Primary
    DeactivateRedirectRulePort adminRedirectTestDeactivateRedirectRulePort(AdminRedirectTestPorts ports) {
        return ports::deactivateCore;
    }

    @Bean
    @Primary
    AdminRedirectCommandGateway adminRedirectTestCommandGateway(AdminRedirectTestPorts ports) {
        return ports;
    }

    static final class AdminRedirectTestPorts implements AdminRedirectCommandGateway {
        private final List<RedirectRuleSummary> rules = new ArrayList<>();
        private RedirectRuleCreationResult createResult = createdResult();
        private DeactivateRedirectRuleResult deactivateResult = deactivatedResult();
        private CreateManualRedirectCommand lastCreateCommand;
        private DeactivateManualRedirectCommand lastDeactivateCommand;
        private RuntimeException createFailure;
        private RuntimeException deactivateFailure;

        RedirectRuleListResult list(RedirectRuleListQuery query) {
            return new RedirectRuleListResult(rules);
        }

        @Override
        public RedirectRuleCreationResult create(CreateManualRedirectCommand command) {
            lastCreateCommand = command;
            if (createFailure != null) {
                throw createFailure;
            }
            return createResult;
        }

        @Override
        public DeactivateRedirectRuleResult deactivate(DeactivateManualRedirectCommand command) {
            lastDeactivateCommand = command;
            if (deactivateFailure != null) {
                throw deactivateFailure;
            }
            return deactivateResult;
        }

        RedirectRuleCreationResult createCore(CreateRedirectRuleCommand command) {
            return new RedirectRuleCreationResult.Created(summary(
                    RedirectRuleId.random(), command.sourceUrl(), command.targetUrl(), command.statusCode()));
        }

        DeactivateRedirectRuleResult deactivateCore(DeactivateRedirectRuleCommand command) {
            return new DeactivateRedirectRuleResult.NotFound(command.redirectRuleId());
        }

        void reset() {
            rules.clear();
            createResult = createdResult();
            deactivateResult = deactivatedResult();
            lastCreateCommand = null;
            lastDeactivateCommand = null;
            createFailure = null;
            deactivateFailure = null;
        }

        void addRule(RedirectRuleSummary rule) {
            rules.add(rule);
        }

        void rejectCreate(RedirectRuleCreationResult.Reason reason) {
            createResult = new RedirectRuleCreationResult.Rejected(reason);
        }

        CreateManualRedirectCommand lastCreateCommand() {
            return lastCreateCommand;
        }

        DeactivateManualRedirectCommand lastDeactivateCommand() {
            return lastDeactivateCommand;
        }

        void failCreate(RuntimeException failure) {
            createFailure = failure;
        }

        void failDeactivate(RuntimeException failure) {
            deactivateFailure = failure;
        }

        private static RedirectRuleCreationResult createdResult() {
            return new RedirectRuleCreationResult.Created(summary(
                    RedirectRuleId.random(),
                    new PublicUrl("/tr/articles/old"),
                    new PublicUrl("/tr/articles/new"),
                    RedirectStatusCode.MOVED_PERMANENTLY_301));
        }

        private static DeactivateRedirectRuleResult deactivatedResult() {
            return new DeactivateRedirectRuleResult.Deactivated(summary(
                    RedirectRuleId.random(),
                    new PublicUrl("/tr/articles/old"),
                    new PublicUrl("/tr/articles/new"),
                    RedirectStatusCode.MOVED_PERMANENTLY_301));
        }

        private static RedirectRuleChangeSummary summary(
                RedirectRuleId id,
                PublicUrl sourceUrl,
                PublicUrl targetUrl,
                RedirectStatusCode statusCode) {
            return new RedirectRuleChangeSummary(id, sourceUrl, targetUrl, statusCode, RedirectReason.MANUAL);
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

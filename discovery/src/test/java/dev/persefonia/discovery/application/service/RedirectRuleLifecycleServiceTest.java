package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleStatusFilter;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedirectRuleLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-14T09:00:00Z");

    @Test
    void deactivatesActiveRule() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        RedirectRule active = rule("active", true);
        repository.rules.add(active);

        var result = service(repository).deactivate(new DeactivateRedirectRuleCommand(active.id()));

        assertThat(result).isInstanceOf(DeactivateRedirectRuleResult.Deactivated.class);
        assertThat(repository.findById(active.id())).hasValueSatisfying(deactivated -> {
            assertThat(deactivated.active()).isFalse();
            assertThat(deactivated.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(deactivated.version().value()).isEqualTo(active.version().value() + 1);
        });
    }

    @Test
    void missingRuleReturnsNotFound() {
        var result = service(new InMemoryRedirectRuleRepository())
                .deactivate(new DeactivateRedirectRuleCommand(RedirectRuleId.random()));

        assertThat(result).isInstanceOf(DeactivateRedirectRuleResult.NotFound.class);
    }

    @Test
    void inactiveRuleReturnsAlreadyInactiveWithoutThrowing() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        RedirectRule inactive = rule("inactive", false);
        repository.rules.add(inactive);

        var result = service(repository).deactivate(new DeactivateRedirectRuleCommand(inactive.id()));

        assertThat(result).isInstanceOf(DeactivateRedirectRuleResult.AlreadyInactive.class);
        assertThat(repository.deactivateCalls).isZero();
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> service(new InMemoryRedirectRuleRepository()).deactivate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RedirectRuleLifecycleService service(RedirectRuleRepository repository) {
        return new RedirectRuleLifecycleService(repository, Clock.fixed(UPDATED_AT, ZoneOffset.UTC));
    }

    private static RedirectRule rule(String suffix, boolean active) {
        return RedirectRule.create(
                RedirectRuleId.random(),
                new PublicUrl("/old-" + suffix),
                new PublicUrl("/new-" + suffix),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.MANUAL,
                null,
                active,
                NOW,
                NOW,
                Version.initial());
    }

    private static final class InMemoryRedirectRuleRepository implements RedirectRuleRepository {
        private final List<RedirectRule> rules = new ArrayList<>();
        private int deactivateCalls;

        @Override
        public RedirectRule save(RedirectRule redirectRule) {
            rules.add(redirectRule);
            return redirectRule;
        }

        @Override
        public Optional<RedirectRule> findById(RedirectRuleId id) {
            return rules.stream().filter(rule -> rule.id().equals(id)).findFirst();
        }

        @Override
        public Optional<RedirectRule> findActiveBySourceUrl(PublicUrl sourceUrl) {
            return rules.stream()
                    .filter(RedirectRule::active)
                    .filter(rule -> rule.sourceUrl().equals(sourceUrl))
                    .findFirst();
        }

        @Override
        public List<RedirectRule> findBySourceRef(SourceEntityRef sourceRef) {
            return List.of();
        }

        @Override
        public List<RedirectRule> list(RedirectRuleStatusFilter status, int limit) {
            return rules.stream().limit(limit).toList();
        }

        @Override
        public Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt) {
            deactivateCalls++;
            for (int index = 0; index < rules.size(); index++) {
                RedirectRule rule = rules.get(index);
                if (rule.id().equals(id) && rule.active()) {
                    RedirectRule deactivated = rule.deactivate(updatedAt);
                    rules.set(index, deactivated);
                    return Optional.of(deactivated);
                }
            }
            return findById(id);
        }
    }
}

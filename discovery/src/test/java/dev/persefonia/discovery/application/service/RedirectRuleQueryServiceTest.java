package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.redirect.RedirectRuleListQuery;
import dev.persefonia.discovery.application.redirect.RedirectRuleStatusFilter;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedirectRuleQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");

    @Test
    void listsActiveInactiveAndAllRules() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        RedirectRule active = rule("active", true, NOW);
        RedirectRule inactive = rule("inactive", false, NOW.plusSeconds(1));
        repository.rules.add(active);
        repository.rules.add(inactive);

        RedirectRuleQueryService service = new RedirectRuleQueryService(repository);

        assertThat(service.list(new RedirectRuleListQuery(RedirectRuleStatusFilter.ACTIVE, 100)).rules())
                .extracting(summary -> summary.id())
                .containsExactly(active.id());
        assertThat(service.list(new RedirectRuleListQuery(RedirectRuleStatusFilter.INACTIVE, 100)).rules())
                .extracting(summary -> summary.id())
                .containsExactly(inactive.id());
        assertThat(service.list(RedirectRuleListQuery.latestAll()).rules())
                .extracting(summary -> summary.id())
                .containsExactly(active.id(), inactive.id());
    }

    @Test
    void listOrderIsDeterministicAndLimited() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        RedirectRule newestInactive = rule("inactive-newest", false, NOW.plusSeconds(20));
        RedirectRule newestActive = rule("b-active", true, NOW.plusSeconds(10));
        RedirectRule sameTimeActive = rule("a-active", true, NOW.plusSeconds(10));
        repository.rules.add(newestInactive);
        repository.rules.add(newestActive);
        repository.rules.add(sameTimeActive);

        var result = new RedirectRuleQueryService(repository)
                .list(new RedirectRuleListQuery(RedirectRuleStatusFilter.ALL, 2));

        assertThat(result.rules())
                .extracting(summary -> summary.sourceUrl().value())
                .containsExactly("/old-a-active", "/old-b-active");
    }

    @Test
    void rejectsNullQuery() {
        assertThatThrownBy(() -> new RedirectRuleQueryService(new InMemoryRedirectRuleRepository()).list(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RedirectRule rule(String suffix, boolean active, Instant createdAt) {
        return RedirectRule.create(
                RedirectRuleId.random(),
                new PublicUrl("/old-" + suffix),
                new PublicUrl("/new-" + suffix),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.MANUAL,
                null,
                active,
                createdAt,
                createdAt,
                Version.initial());
    }

    private static final class InMemoryRedirectRuleRepository implements RedirectRuleRepository {
        private final List<RedirectRule> rules = new ArrayList<>();

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
            return rules.stream()
                    .filter(rule -> switch (status) {
                        case ACTIVE -> rule.active();
                        case INACTIVE -> !rule.active();
                        case ALL -> true;
                    })
                    .sorted(Comparator.comparing(RedirectRule::active).reversed()
                            .thenComparing(RedirectRule::createdAt, Comparator.reverseOrder())
                            .thenComparing(rule -> rule.sourceUrl().value()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt) {
            return Optional.empty();
        }
    }
}

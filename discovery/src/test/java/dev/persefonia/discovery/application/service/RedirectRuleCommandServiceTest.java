package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedirectRuleCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");

    @Test
    void createsManualRedirect() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();

        RedirectRuleCreationResult result = service(repository).create(manual("/old", "/new"));

        assertThat(result).isInstanceOf(RedirectRuleCreationResult.Created.class);
        assertThat(repository.saved).singleElement().satisfies(rule -> {
            assertThat(rule.reason()).isEqualTo(RedirectReason.MANUAL);
            assertThat(rule.sourceRef()).isEmpty();
            assertThat(rule.createdAt()).isEqualTo(NOW);
            assertThat(rule.version()).isEqualTo(Version.initial());
        });
    }

    @Test
    void createsSlugChangedRedirectWithSourceReference() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();

        RedirectRuleCreationResult result = service(repository).create(slugChanged("/old", "/new"));

        assertThat(result).isInstanceOf(RedirectRuleCreationResult.Created.class);
        assertThat(repository.saved).singleElement().satisfies(rule -> {
            assertThat(rule.reason()).isEqualTo(RedirectReason.SLUG_CHANGED);
            assertThat(rule.statusCode()).isEqualTo(RedirectStatusCode.MOVED_PERMANENTLY_301);
            assertThat(rule.sourceRef()).contains(sourceRef());
        });
    }

    @Test
    void returnsNoopForIdenticalActiveRedirect() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        repository.active.add(rule(manual("/old", "/new")));

        RedirectRuleCreationResult result = service(repository).create(manual("/old", "/new"));

        assertThat(result).isInstanceOf(RedirectRuleCreationResult.Noop.class);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void rejectsDifferentRedirectForDuplicateActiveSource() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        repository.active.add(rule(manual("/old", "/first-target")));

        RedirectRuleCreationResult result = service(repository).create(manual("/old", "/different-target"));

        assertThat(result).isEqualTo(new RedirectRuleCreationResult.Rejected(
                RedirectRuleCreationResult.Reason.DUPLICATE_ACTIVE_SOURCE));
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void rejectsDirectLoop() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        repository.active.add(rule(manual("/b", "/a")));

        RedirectRuleCreationResult result = service(repository).create(manual("/a", "/b"));

        assertThat(result).isEqualTo(
                new RedirectRuleCreationResult.Rejected(RedirectRuleCreationResult.Reason.LOOP_DETECTED));
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void doesNotTraverseMultiHopRedirectChains() {
        InMemoryRedirectRuleRepository repository = new InMemoryRedirectRuleRepository();
        repository.active.add(rule(manual("/b", "/c")));
        repository.active.add(rule(manual("/c", "/a")));

        RedirectRuleCreationResult result = service(repository).create(manual("/a", "/b"));

        assertThat(result).isInstanceOf(RedirectRuleCreationResult.Created.class);
    }

    @Test
    void rejectsSelfRedirectAndNullCommand() {
        assertThatThrownBy(() -> manual("/same", "/same")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(new InMemoryRedirectRuleRepository()).create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RedirectRuleCommandService service(RedirectRuleRepository repository) {
        return new RedirectRuleCommandService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateRedirectRuleCommand manual(String source, String target) {
        return new CreateRedirectRuleCommand(
                new PublicUrl(source),
                new PublicUrl(target),
                RedirectStatusCode.FOUND_302,
                RedirectReason.MANUAL,
                null,
                null,
                null);
    }

    private static CreateRedirectRuleCommand slugChanged(String source, String target) {
        SourceEntityRef sourceRef = sourceRef();
        return new CreateRedirectRuleCommand(
                new PublicUrl(source),
                new PublicUrl(target),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.SLUG_CHANGED,
                sourceRef.sourceContext(),
                sourceRef.sourceType(),
                sourceRef.sourceEntityId());
    }

    private static RedirectRule rule(CreateRedirectRuleCommand command) {
        SourceEntityRef sourceRef = command.sourceContext() == null
                ? null
                : new SourceEntityRef(command.sourceContext(), command.sourceType(), command.sourceEntityId());
        return RedirectRule.create(
                RedirectRuleId.random(),
                command.sourceUrl(),
                command.targetUrl(),
                command.statusCode(),
                command.reason(),
                sourceRef,
                true,
                NOW,
                NOW,
                Version.initial());
    }

    private static SourceEntityRef sourceRef() {
        return new SourceEntityRef(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(UUID.fromString("5b91a38c-bddc-439b-b89a-5c42231b62ad")));
    }

    private static final class InMemoryRedirectRuleRepository implements RedirectRuleRepository {
        private final List<RedirectRule> active = new ArrayList<>();
        private final List<RedirectRule> saved = new ArrayList<>();

        @Override
        public RedirectRule save(RedirectRule redirectRule) {
            saved.add(redirectRule);
            active.add(redirectRule);
            return redirectRule;
        }

        @Override
        public Optional<RedirectRule> findById(RedirectRuleId id) {
            return active.stream().filter(rule -> rule.id().equals(id)).findFirst();
        }

        @Override
        public Optional<RedirectRule> findActiveBySourceUrl(PublicUrl sourceUrl) {
            return active.stream()
                    .filter(RedirectRule::active)
                    .filter(rule -> rule.sourceUrl().equals(sourceUrl))
                    .findFirst();
        }

        @Override
        public List<RedirectRule> findBySourceRef(SourceEntityRef sourceRef) {
            return active.stream().filter(rule -> rule.sourceRef().equals(Optional.of(sourceRef))).toList();
        }

        @Override
        public Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt) {
            return Optional.empty();
        }
    }
}

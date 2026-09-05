package dev.persefonia.app.discovery.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.redirect.RedirectRuleStatusFilter;
import dev.persefonia.discovery.domain.RedirectRule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostgresRedirectRuleRepositoryTest extends DiscoveryRepositoryTestDatabase {
    @Test
    void savesAndFindsManualAndSlugChangedRedirects() {
        RedirectRule manual = redirects.save(DiscoveryRepositoryFixtures.manualRedirect("manual", true));
        RedirectRule slugChanged = redirects.save(dev.persefonia.discovery.domain.RedirectRule.createSlugChanged(
                dev.persefonia.discovery.domain.RedirectRuleId.random(),
                new PublicUrl("/old-slug"),
                new PublicUrl("/new-slug"),
                DiscoveryRepositoryFixtures.SOURCE_REF,
                DiscoveryRepositoryFixtures.NOW,
                dev.persefonia.discovery.domain.Version.initial()));

        assertThat(redirects.findById(manual.id())).hasValueSatisfying(
                actual -> assertThat(actual).usingRecursiveComparison().isEqualTo(manual));
        assertThat(redirects.findActiveBySourceUrl(manual.sourceUrl())).hasValueSatisfying(
                actual -> assertThat(actual).usingRecursiveComparison().isEqualTo(manual));
        assertThat(redirects.findBySourceRef(DiscoveryRepositoryFixtures.SOURCE_REF))
                .extracting(rule -> rule.id())
                .containsExactlyInAnyOrder(manual.id(), slugChanged.id());
    }

    @Test
    void activeLookupIgnoresInactiveRedirectAndInactiveDuplicatesCanCoexist() {
        RedirectRule inactive = redirects.save(DiscoveryRepositoryFixtures.manualRedirect("duplicate", false));
        RedirectRule secondInactive = redirects.save(DiscoveryRepositoryFixtures.manualRedirect("duplicate", false));

        assertThat(redirects.findActiveBySourceUrl(inactive.sourceUrl())).isEmpty();
        assertThat(secondInactive.sourceUrl()).isEqualTo(inactive.sourceUrl());
    }

    @Test
    void duplicateActiveSourceUrlIsRejected() {
        redirects.save(DiscoveryRepositoryFixtures.manualRedirect("active-duplicate", true));

        assertThatThrownBy(() -> redirects.save(DiscoveryRepositoryFixtures.manualRedirect("active-duplicate", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsRedirectRulesByStatusInDeterministicOrder() {
        RedirectRule olderActive = redirects.save(manualRedirectAt("older", true, DiscoveryRepositoryFixtures.NOW));
        RedirectRule newestInactive = redirects.save(manualRedirectAt(
                "newest-inactive", false, DiscoveryRepositoryFixtures.NOW.plusSeconds(40)));
        RedirectRule sameTimeB = redirects.save(manualRedirectAt(
                "same-b", true, DiscoveryRepositoryFixtures.NOW.plusSeconds(20)));
        RedirectRule sameTimeA = redirects.save(manualRedirectAt(
                "same-a", true, DiscoveryRepositoryFixtures.NOW.plusSeconds(20)));

        assertThat(redirects.list(RedirectRuleStatusFilter.ACTIVE, 100))
                .extracting(rule -> rule.id())
                .containsExactly(sameTimeA.id(), sameTimeB.id(), olderActive.id());
        assertThat(redirects.list(RedirectRuleStatusFilter.INACTIVE, 100))
                .extracting(rule -> rule.id())
                .containsExactly(newestInactive.id());
        assertThat(redirects.list(RedirectRuleStatusFilter.ALL, 2))
                .extracting(rule -> rule.id())
                .containsExactly(sameTimeA.id(), sameTimeB.id());
    }

    @Test
    void deactivateAdvancesVersionOnceAndIsIdempotent() {
        RedirectRule active = redirects.save(DiscoveryRepositoryFixtures.manualRedirect("deactivate", true));
        Instant deactivatedAt = DiscoveryRepositoryFixtures.NOW.plusSeconds(30);

        RedirectRule deactivated = redirects.deactivate(active.id(), deactivatedAt).orElseThrow();
        RedirectRule repeated = redirects.deactivate(active.id(), deactivatedAt.plusSeconds(30)).orElseThrow();

        assertThat(deactivated.active()).isFalse();
        assertThat(deactivated.updatedAt()).isEqualTo(deactivatedAt);
        assertThat(deactivated.version().value()).isEqualTo(active.version().value() + 1);
        assertThat(repeated).usingRecursiveComparison().isEqualTo(deactivated);
        assertThat(redirects.findActiveBySourceUrl(active.sourceUrl())).isEmpty();
    }

    @Test
    void deactivatedSourceUrlCanBeReusedByNewActiveRedirect() {
        RedirectRule active = redirects.save(DiscoveryRepositoryFixtures.manualRedirect("reuse", true));
        redirects.deactivate(active.id(), DiscoveryRepositoryFixtures.NOW.plusSeconds(30));

        RedirectRule replacement = RedirectRule.create(
                dev.persefonia.discovery.domain.RedirectRuleId.random(),
                active.sourceUrl(),
                new PublicUrl("/replacement-reuse"),
                active.statusCode(),
                active.reason(),
                null,
                true,
                DiscoveryRepositoryFixtures.NOW.plusSeconds(60),
                DiscoveryRepositoryFixtures.NOW.plusSeconds(60),
                dev.persefonia.discovery.domain.Version.initial());

        RedirectRule saved = redirects.save(replacement);

        assertThat(redirects.findActiveBySourceUrl(active.sourceUrl()))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(saved.id()));
    }

    private static RedirectRule manualRedirectAt(String suffix, boolean active, Instant createdAt) {
        return RedirectRule.create(
                dev.persefonia.discovery.domain.RedirectRuleId.random(),
                new PublicUrl("/old-" + suffix),
                new PublicUrl("/new-" + suffix),
                dev.persefonia.discovery.application.contract.RedirectStatusCode.PERMANENT_REDIRECT_308,
                dev.persefonia.discovery.application.contract.RedirectReason.MANUAL,
                null,
                active,
                createdAt,
                createdAt,
                dev.persefonia.discovery.domain.Version.initial());
    }
}

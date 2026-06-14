package dev.persefonia.app.discovery.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
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
                .extracting(RedirectRule::id)
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
}

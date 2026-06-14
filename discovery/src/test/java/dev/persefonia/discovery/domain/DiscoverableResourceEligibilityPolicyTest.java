package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import org.junit.jupiter.api.Test;

class DiscoverableResourceEligibilityPolicyTest {
    @Test
    void listedArticleNoteAndResearchAreEligibleForIndexSearchSitemapAndFeed() {
        for (DiscoverableResourceType type : new DiscoverableResourceType[] {
                DiscoverableResourceType.ARTICLE,
                DiscoverableResourceType.NOTE,
                DiscoverableResourceType.RESEARCH
        }) {
            assertThat(DiscoverableResourceEligibilityPolicy.listedFor(type))
                    .isEqualTo(new DiscoverableResourceEligibility(
                            IndexingPolicy.INDEX,
                            DiscoveryEligibility.ELIGIBLE,
                            DiscoveryEligibility.ELIGIBLE,
                            DiscoveryEligibility.ELIGIBLE));
        }
    }

    @Test
    void listedPageIsEligibleExceptForFeed() {
        assertThat(DiscoverableResourceEligibilityPolicy.listedFor(DiscoverableResourceType.PAGE))
                .isEqualTo(new DiscoverableResourceEligibility(
                        IndexingPolicy.INDEX,
                        DiscoveryEligibility.ELIGIBLE,
                        DiscoveryEligibility.ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE));
    }

    @Test
    void unlistedIsNotEligibleForIndexSearchSitemapOrFeed() {
        assertThat(DiscoverableResourceEligibilityPolicy.unlisted())
                .isEqualTo(new DiscoverableResourceEligibility(
                        IndexingPolicy.NO_INDEX,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE,
                        DiscoveryEligibility.NOT_ELIGIBLE));
    }
}

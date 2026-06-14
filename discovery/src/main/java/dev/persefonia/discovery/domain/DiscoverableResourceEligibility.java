package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import java.util.Objects;

public record DiscoverableResourceEligibility(
        IndexingPolicy indexingPolicy,
        DiscoveryEligibility searchEligibility,
        DiscoveryEligibility sitemapEligibility,
        DiscoveryEligibility feedEligibility) {
    public DiscoverableResourceEligibility {
        Objects.requireNonNull(indexingPolicy, "indexingPolicy");
        Objects.requireNonNull(searchEligibility, "searchEligibility");
        Objects.requireNonNull(sitemapEligibility, "sitemapEligibility");
        Objects.requireNonNull(feedEligibility, "feedEligibility");
    }
}

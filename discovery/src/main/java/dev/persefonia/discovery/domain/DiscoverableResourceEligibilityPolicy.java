package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import java.util.Objects;

public final class DiscoverableResourceEligibilityPolicy {
    private DiscoverableResourceEligibilityPolicy() {
    }

    public static DiscoverableResourceEligibility listedFor(DiscoverableResourceType type) {
        Objects.requireNonNull(type, "type");
        DiscoveryEligibility feedEligibility = type == DiscoverableResourceType.PAGE
                ? DiscoveryEligibility.NOT_ELIGIBLE
                : DiscoveryEligibility.ELIGIBLE;
        return new DiscoverableResourceEligibility(
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                feedEligibility);
    }

    public static DiscoverableResourceEligibility unlisted() {
        return new DiscoverableResourceEligibility(
                IndexingPolicy.NO_INDEX,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE);
    }
}

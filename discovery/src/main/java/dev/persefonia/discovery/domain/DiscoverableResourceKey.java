package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.Objects;

public record DiscoverableResourceKey(
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId,
        DiscoverableResourceType resourceType,
        DiscoveryLanguage language,
        RoutePurpose routePurpose) {
    public DiscoverableResourceKey {
        Objects.requireNonNull(sourceContext, "sourceContext");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(routePurpose, "routePurpose");
    }

    public SourceEntityRef sourceRef() {
        return new SourceEntityRef(sourceContext, sourceType, sourceEntityId);
    }
}

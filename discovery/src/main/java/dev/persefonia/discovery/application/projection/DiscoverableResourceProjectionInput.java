package dev.persefonia.discovery.application.projection;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Desired current public-discovery projection supplied by a source context.
 * Open Graph metadata, asset id, and timestamps are optional.
 */
public record DiscoverableResourceProjectionInput(
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId,
        DiscoverableResourceType resourceType,
        RoutePurpose routePurpose,
        DiscoveryLanguage language,
        PublicUrl publicUrl,
        CanonicalUrl canonicalUrl,
        String title,
        String summary,
        IndexingPolicy indexingPolicy,
        DiscoveryEligibility searchEligibility,
        DiscoveryEligibility sitemapEligibility,
        DiscoveryEligibility feedEligibility,
        String openGraphTitle,
        String openGraphDescription,
        UUID openGraphImageAssetId,
        Instant publishedAt,
        Instant sourceUpdatedAt,
        String searchText) {
    public DiscoverableResourceProjectionInput {
        Objects.requireNonNull(sourceContext, "sourceContext");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(routePurpose, "routePurpose");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        requireNonBlank(title, "title");
        requireNonBlank(summary, "summary");
        Objects.requireNonNull(indexingPolicy, "indexingPolicy");
        Objects.requireNonNull(searchEligibility, "searchEligibility");
        Objects.requireNonNull(sitemapEligibility, "sitemapEligibility");
        Objects.requireNonNull(feedEligibility, "feedEligibility");
        requireNonBlank(searchText, "searchText");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

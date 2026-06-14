package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import java.util.Objects;
import java.util.Optional;

public final class ContentDiscoveryProjectionFactory {
    private final ContentPublicRouteFactory routeFactory;
    private final ConfiguredContentCanonicalUrlFactory canonicalUrlFactory;

    public ContentDiscoveryProjectionFactory(
            ContentPublicRouteFactory routeFactory,
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    public Optional<DiscoverableResourceProjectionInput> projectionFor(ContentItem item) {
        Objects.requireNonNull(item, "item");
        if (!item.isPublished() || item.visibility() == ContentVisibility.PRIVATE) {
            return Optional.empty();
        }

        ContentMetadata metadata = item.metadata();
        var resourceType = resourceType(item.type());
        var eligibility = eligibility(item.visibility(), resourceType);
        var publicUrl = routeFactory.publicUrl(item.type(), item.language(), item.slug().orElseThrow());
        return Optional.of(new DiscoverableResourceProjectionInput(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(item.id().value()),
                resourceType,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.valueOf(item.language().name()),
                publicUrl,
                canonicalUrlFactory.canonicalUrl(publicUrl),
                item.title().orElseThrow().value(),
                item.summary().orElseThrow().value(),
                eligibility.indexingPolicy(),
                eligibility.searchEligibility(),
                eligibility.sitemapEligibility(),
                eligibility.feedEligibility(),
                metadata.openGraphTitle().map(title -> title.value()).orElse(null),
                metadata.openGraphDescription().map(description -> description.value()).orElse(null),
                metadata.ogImageAssetId().map(assetId -> assetId.value()).orElse(null),
                item.publishedAt().orElseThrow(),
                item.updatedAt(),
                searchText(item)));
    }

    public RemoveDiscoverableResourceCommand removeCommandFor(ContentItem item) {
        Objects.requireNonNull(item, "item");
        return new RemoveDiscoverableResourceCommand(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(item.id().value()));
    }

    private static DiscoverableResourceType resourceType(ContentType type) {
        return DiscoverableResourceType.valueOf(type.name());
    }

    private static ProjectionEligibility eligibility(
            ContentVisibility visibility, DiscoverableResourceType resourceType) {
        if (visibility == ContentVisibility.UNLISTED) {
            return new ProjectionEligibility(
                    IndexingPolicy.NO_INDEX,
                    DiscoveryEligibility.NOT_ELIGIBLE,
                    DiscoveryEligibility.NOT_ELIGIBLE,
                    DiscoveryEligibility.NOT_ELIGIBLE);
        }

        DiscoveryEligibility feedEligibility = resourceType == DiscoverableResourceType.PAGE
                ? DiscoveryEligibility.NOT_ELIGIBLE
                : DiscoveryEligibility.ELIGIBLE;
        return new ProjectionEligibility(
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                feedEligibility);
    }

    private static String searchText(ContentItem item) {
        return item.title().orElseThrow().value() + "\n" + item.summary().orElseThrow().value();
    }

    private record ProjectionEligibility(
            IndexingPolicy indexingPolicy,
            DiscoveryEligibility searchEligibility,
            DiscoveryEligibility sitemapEligibility,
            DiscoveryEligibility feedEligibility) {
    }
}

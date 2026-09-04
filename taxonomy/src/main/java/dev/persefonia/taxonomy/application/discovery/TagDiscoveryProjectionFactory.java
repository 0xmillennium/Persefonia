package dev.persefonia.taxonomy.application.discovery;

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
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.taxonomy.domain.model.Tag;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TagDiscoveryProjectionFactory {
    private final URI publicBaseUrl;
    private final TagPublicRouteFactory routeFactory;

    public TagDiscoveryProjectionFactory(String publicBaseUrl) {
        this(publicBaseUrl, new TagPublicRouteFactory());
    }

    public TagDiscoveryProjectionFactory(String publicBaseUrl, TagPublicRouteFactory routeFactory) {
        Objects.requireNonNull(publicBaseUrl, "publicBaseUrl");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        URI parsed = URI.create(publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl);
        if (!parsed.isAbsolute()) {
            throw new IllegalArgumentException("publicBaseUrl must be absolute");
        }
        this.publicBaseUrl = parsed;
    }

    public List<DiscoverableResourceProjectionInput> projectionsFor(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        return List.of(projectionFor(tag, DiscoveryLanguage.TR), projectionFor(tag, DiscoveryLanguage.EN));
    }

    private DiscoverableResourceProjectionInput projectionFor(Tag tag, DiscoveryLanguage language) {
        String path = routeFactory.publicUrl(language, tag.slug()).value();
        String description = tag.description().value().orElse("Content tagged " + tag.name().value());
        return new DiscoverableResourceProjectionInput(
                SourceContext.TAXONOMY,
                SourceType.TAG,
                new SourceEntityId(tag.id().value()),
                DiscoverableResourceType.TAG,
                RoutePurpose.TAG_PAGE,
                language,
                new PublicUrl(path),
                new CanonicalUrl(publicBaseUrl + path),
                tag.name().value(),
                description,
                IndexingPolicy.NO_INDEX,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                null,
                null,
                null,
                null,
                tag.updatedAt(),
                tag.name().value() + "\n" + description);
    }
}

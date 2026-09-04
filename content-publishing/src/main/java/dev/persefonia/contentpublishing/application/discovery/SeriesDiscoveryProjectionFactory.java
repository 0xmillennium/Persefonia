package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
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
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SeriesDiscoveryProjectionFactory {
    private final ConfiguredContentCanonicalUrlFactory canonicalUrlFactory;
    private final SeriesPublicRouteFactory routeFactory;

    public SeriesDiscoveryProjectionFactory(ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        this(new SeriesPublicRouteFactory(), canonicalUrlFactory);
    }

    public SeriesDiscoveryProjectionFactory(
            SeriesPublicRouteFactory routeFactory,
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    public Optional<DiscoverableResourceProjectionInput> projectionFor(Series series) {
        Objects.requireNonNull(series, "series");
        if (series.isArchived()) {
            return Optional.empty();
        }
        PublicUrl publicUrl = routeFactory.publicUrl(series);
        String summary = summary(series);
        return Optional.of(new DiscoverableResourceProjectionInput(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.SERIES,
                new SourceEntityId(series.id().value()),
                DiscoverableResourceType.SERIES,
                RoutePurpose.SERIES_PAGE,
                DiscoveryLanguage.valueOf(series.language().name()),
                publicUrl,
                canonicalUrlFactory.canonicalUrl(publicUrl),
                series.title().value(),
                summary,
                IndexingPolicy.NO_INDEX,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                null,
                null,
                null,
                null,
                series.updatedAt(),
                series.title().value() + "\n" + summary));
    }

    public RemoveDiscoverableResourceCommand removeCommandFor(Series series) {
        Objects.requireNonNull(series, "series");
        return new RemoveDiscoverableResourceCommand(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.SERIES,
                new SourceEntityId(series.id().value()));
    }

    private static String summary(Series series) {
        return series.description().map(SeriesDescription::value).orElse(series.title().value());
    }
}

package dev.persefonia.webpublic.series;

import dev.persefonia.contentpublishing.application.query.PublicSeriesBySourceQuery;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.Objects;

public sealed interface DiscoveryPublicSeriesRouteOutcome
        permits DiscoveryPublicSeriesRouteOutcome.Series, DiscoveryPublicSeriesRouteOutcome.NotFound {
    record Series(
            PublicSeriesBySourceQuery query,
            DiscoveryLanguage language,
            String publicUrl,
            String canonicalUrl) implements DiscoveryPublicSeriesRouteOutcome {
        public Series {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(publicUrl, "publicUrl");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        }
    }

    record NotFound() implements DiscoveryPublicSeriesRouteOutcome {
    }
}

package dev.persefonia.webpublic.tags;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.taxonomy.application.query.PublicTagBySourceQuery;
import java.util.Objects;

public sealed interface DiscoveryPublicTagRouteOutcome
        permits DiscoveryPublicTagRouteOutcome.Tag, DiscoveryPublicTagRouteOutcome.NotFound {
    record Tag(
            PublicTagBySourceQuery query,
            DiscoveryLanguage language,
            String publicUrl,
            String canonicalUrl) implements DiscoveryPublicTagRouteOutcome {
        public Tag {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(publicUrl, "publicUrl");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        }
    }

    record NotFound() implements DiscoveryPublicTagRouteOutcome {
    }
}

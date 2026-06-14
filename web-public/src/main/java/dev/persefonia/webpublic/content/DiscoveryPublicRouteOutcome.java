package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import java.util.Objects;

public sealed interface DiscoveryPublicRouteOutcome
        permits DiscoveryPublicRouteOutcome.Content,
                DiscoveryPublicRouteOutcome.Redirect,
                DiscoveryPublicRouteOutcome.NotFound {

    record Content(PublicContentBySourceQuery query) implements DiscoveryPublicRouteOutcome {
        public Content {
            Objects.requireNonNull(query, "query");
        }
    }

    record Redirect(int statusCode, String targetPath) implements DiscoveryPublicRouteOutcome {
        public Redirect {
            Objects.requireNonNull(targetPath, "targetPath");
        }
    }

    record NotFound() implements DiscoveryPublicRouteOutcome {
    }
}

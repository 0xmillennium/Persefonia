package dev.persefonia.discovery.application.route;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.Objects;

public sealed interface PublicRouteResolution
        permits PublicRouteResolution.Found,
                PublicRouteResolution.Redirect,
                PublicRouteResolution.NotFound {

    record Found(
            SourceContext sourceContext,
            SourceType sourceType,
            SourceEntityId sourceEntityId,
            DiscoverableResourceType resourceType,
            RoutePurpose routePurpose,
            DiscoveryLanguage language,
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            IndexingPolicy indexingPolicy) implements PublicRouteResolution {
        public Found {
            Objects.requireNonNull(sourceContext, "sourceContext");
            Objects.requireNonNull(sourceType, "sourceType");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(routePurpose, "routePurpose");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(publicUrl, "publicUrl");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
            Objects.requireNonNull(indexingPolicy, "indexingPolicy");
        }
    }

    record Redirect(
            RedirectStatusCode statusCode,
            PublicUrl targetUrl) implements PublicRouteResolution {
        public Redirect {
            Objects.requireNonNull(statusCode, "statusCode");
            Objects.requireNonNull(targetUrl, "targetUrl");
        }
    }

    record NotFound() implements PublicRouteResolution {
    }
}

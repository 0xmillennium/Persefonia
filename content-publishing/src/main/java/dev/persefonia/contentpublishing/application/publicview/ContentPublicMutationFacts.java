package dev.persefonia.contentpublishing.application.publicview;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;
import java.util.Optional;

public record ContentPublicMutationFacts(
        ContentId contentId,
        ContentPublicExposureSnapshot beforeExposure,
        ContentPublicExposureSnapshot afterExposure,
        Optional<PublicUrl> oldPublicRoute,
        Optional<PublicUrl> currentPublicRoute) {
    public ContentPublicMutationFacts {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(beforeExposure, "beforeExposure");
        Objects.requireNonNull(afterExposure, "afterExposure");
        oldPublicRoute = Objects.requireNonNull(oldPublicRoute, "oldPublicRoute");
        currentPublicRoute = Objects.requireNonNull(currentPublicRoute, "currentPublicRoute");
    }
}

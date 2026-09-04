package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.util.Objects;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Optional;

public record SeriesResult(
        SeriesId seriesId, ContentId contentItemId, boolean mutated,
        Optional<PublicUrl> oldPublicRoute, Optional<PublicUrl> currentPublicRoute) {
    public SeriesResult(SeriesId seriesId) {
        this(seriesId, null, true, Optional.empty(), Optional.empty());
    }

    public SeriesResult(SeriesId seriesId, ContentId contentItemId) {
        this(seriesId, contentItemId, true, Optional.empty(), Optional.empty());
    }

    public SeriesResult(SeriesId seriesId, ContentId contentItemId, boolean mutated) {
        this(seriesId, contentItemId, mutated, Optional.empty(), Optional.empty());
    }

    public SeriesResult {
        Objects.requireNonNull(seriesId, "seriesId");
        oldPublicRoute = Objects.requireNonNull(oldPublicRoute, "oldPublicRoute");
        currentPublicRoute = Objects.requireNonNull(currentPublicRoute, "currentPublicRoute");
    }
}

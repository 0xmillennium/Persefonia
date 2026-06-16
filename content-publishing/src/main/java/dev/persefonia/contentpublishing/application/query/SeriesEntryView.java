package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import java.util.Objects;
import java.util.Optional;

public record SeriesEntryView(
        SeriesEntryId id,
        ContentId contentItemId,
        int position,
        ContentType contentType,
        ContentStatus status,
        Optional<String> title) {
    public SeriesEntryView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(status, "status");
        title = Objects.requireNonNull(title, "title");
    }
}

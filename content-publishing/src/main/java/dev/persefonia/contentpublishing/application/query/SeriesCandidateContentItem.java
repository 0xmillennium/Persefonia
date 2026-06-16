package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.util.Objects;
import java.util.Optional;

public record SeriesCandidateContentItem(
        ContentId contentItemId,
        ContentType contentType,
        ContentStatus status,
        Optional<String> title) {
    public SeriesCandidateContentItem {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(status, "status");
        title = Objects.requireNonNull(title, "title");
    }
}

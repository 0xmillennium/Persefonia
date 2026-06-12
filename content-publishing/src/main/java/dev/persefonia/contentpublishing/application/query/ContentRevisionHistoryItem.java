package dev.persefonia.contentpublishing.application.query;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ContentRevisionHistoryItem(
        int revisionNumber,
        String revisionType,
        String title,
        String slug,
        String createdBy,
        Instant createdAt,
        Optional<String> changeNote,
        boolean renderedHtmlPresent) {
    public ContentRevisionHistoryItem {
        Objects.requireNonNull(revisionType, "revisionType");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        changeNote = Objects.requireNonNull(changeNote, "changeNote");
    }
}

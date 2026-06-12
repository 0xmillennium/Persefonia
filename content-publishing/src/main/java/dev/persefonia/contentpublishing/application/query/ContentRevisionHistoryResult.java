package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContentRevisionHistoryResult(
        ContentId contentId,
        String contentStatus,
        Optional<String> contentTitle,
        List<ContentRevisionHistoryItem> revisions) {
    public ContentRevisionHistoryResult {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(contentStatus, "contentStatus");
        contentTitle = Objects.requireNonNull(contentTitle, "contentTitle");
        revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions"));
    }
}

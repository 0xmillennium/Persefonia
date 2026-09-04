package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.Optional;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;

public record ContentDraftResult(
        ContentId contentId,
        ContentStatus status,
        ContentVisibility visibility,
        ContentLanguage language,
        Optional<Slug> slug,
        Optional<Title> title,
        Optional<Summary> summary,
        Instant createdAt,
        Instant updatedAt,
        Version version,
        ContentPublicMutationFacts publicMutationFacts) {
    public ContentDraftResult(
            ContentId contentId, ContentStatus status, ContentVisibility visibility, ContentLanguage language,
            Optional<Slug> slug, Optional<Title> title, Optional<Summary> summary,
            Instant createdAt, Instant updatedAt, Version version) {
        this(contentId, status, visibility, language, slug, title, summary, createdAt, updatedAt, version,
                new ContentPublicMutationFacts(contentId, ContentPublicExposureSnapshot.none(),
                        ContentPublicExposureSnapshot.none(), Optional.empty(), Optional.empty()));
    }
}

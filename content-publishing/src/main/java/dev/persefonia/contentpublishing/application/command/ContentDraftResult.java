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
        Version version) {
}

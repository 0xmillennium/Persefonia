package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.time.Instant;
import java.util.Optional;

public record AdminContentListItem(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        ContentStatus status,
        ContentVisibility visibility,
        Optional<String> slug,
        Optional<String> title,
        boolean previewAvailable,
        Instant updatedAt) {
}

package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.Optional;

public record AdminContentEditResult(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        ContentStatus status,
        ContentVisibility visibility,
        Optional<String> slug,
        Optional<String> title,
        Optional<String> summary,
        Optional<String> markdownSource,
        Optional<String> metaTitle,
        Optional<String> metaDescription,
        Optional<String> canonicalPath,
        Optional<String> ogTitle,
        Optional<String> ogDescription,
        Optional<String> ogImageAssetId,
        Instant updatedAt,
        Version version) {
}

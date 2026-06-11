package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.Objects;

public record UpdateContentDraftCommand(
        ContentCommandActor actor,
        ContentId contentId,
        ContentFieldUpdate<Slug> slug,
        ContentFieldUpdate<Title> title,
        ContentFieldUpdate<Summary> summary,
        ContentFieldUpdate<MarkdownSource> markdownSource,
        ContentFieldUpdate<ContentMetadata> metadata,
        ContentFieldUpdate<ContentVisibility> visibility,
        Instant requestedAt) {
    public UpdateContentDraftCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(markdownSource, "markdownSource");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (metadata.specified() && metadata.value() == null) {
            throw new IllegalArgumentException("content metadata cannot be cleared");
        }
        if (visibility.specified() && visibility.value() == null) {
            throw new IllegalArgumentException("content visibility cannot be cleared");
        }
    }
}

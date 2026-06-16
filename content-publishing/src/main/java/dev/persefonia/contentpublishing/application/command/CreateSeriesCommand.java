package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import java.time.Instant;
import java.util.Objects;

public record CreateSeriesCommand(
        ContentCommandActor actor,
        ContentLanguage language,
        String title,
        String slug,
        String description,
        Instant requestedAt) {
    public CreateSeriesCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}

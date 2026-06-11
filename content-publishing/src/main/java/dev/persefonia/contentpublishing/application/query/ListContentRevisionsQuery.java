package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.util.Objects;

public record ListContentRevisionsQuery(ContentCommandActor actor, ContentId contentId) {
    public ListContentRevisionsQuery {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
    }
}

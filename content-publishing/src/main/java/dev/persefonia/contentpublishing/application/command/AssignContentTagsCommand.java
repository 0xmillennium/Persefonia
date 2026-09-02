package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.TagId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AssignContentTagsCommand(
        ContentCommandActor actor,
        ContentId contentId,
        List<TagId> requestedTagIds,
        Instant assignedAt) {
    public AssignContentTagsCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
        requestedTagIds = List.copyOf(Objects.requireNonNull(requestedTagIds, "requestedTagIds"));
        Objects.requireNonNull(assignedAt, "assignedAt");
    }
}

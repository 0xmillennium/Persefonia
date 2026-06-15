package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.exception.ContentTagAssignmentRejectedException;
import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.query.ContentTagAssignmentView;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ContentTagAssignmentService {
    public static final int MAX_TAGS = 12;

    private final ContentItemRepository contentItems;
    private final ContentTagAssignmentStore assignments;
    private final ContentTagVocabularyPort vocabulary;
    private final ContentCommandAuthorizationPolicy authorization;

    public ContentTagAssignmentService(
            ContentItemRepository contentItems,
            ContentTagAssignmentStore assignments,
            ContentTagVocabularyPort vocabulary,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ContentTagAssignmentView view(ContentCommandActor actor, ContentId contentId) {
        authorization.requireOwner(actor, "content.tag.admin-view");
        requiredContent(contentItems, contentId);
        Set<ReferencedTagId> assigned = assignments.findAssignedTagIds(contentId);
        return new ContentTagAssignmentView(contentId, vocabulary.findAssignableTags(), vocabulary.findByIds(assigned));
    }

    public void assign(AssignContentTagsCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "content.tag.assign");
        requiredContent(contentItems, command.contentId());

        Set<ReferencedTagId> requested = new LinkedHashSet<>(command.requestedTagIds());
        if (requested.size() > MAX_TAGS) {
            throw new ContentTagAssignmentRejectedException(
                    ContentTagAssignmentRejectedException.Reason.TOO_MANY_TAGS,
                    "A content item may have at most " + MAX_TAGS + " tags.");
        }

        Set<ReferencedTagId> current = assignments.findAssignedTagIds(command.contentId());
        var validation = vocabulary.validateAssignments(current, requested);
        if (!validation.missingTagIds().isEmpty()) {
            throw new ContentTagAssignmentRejectedException(
                    ContentTagAssignmentRejectedException.Reason.MISSING_TAG,
                    "One or more requested tags do not exist.");
        }
        if (!validation.newlyArchivedTagIds().isEmpty()) {
            throw new ContentTagAssignmentRejectedException(
                    ContentTagAssignmentRejectedException.Reason.ARCHIVED_TAG,
                    "Archived tags cannot be newly assigned.");
        }

        assignments.replaceAssignedTagIds(command.contentId(), requested, command.assignedAt());
    }
}

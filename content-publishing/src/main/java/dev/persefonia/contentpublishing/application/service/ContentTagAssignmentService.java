package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.exception.ContentTagAssignmentRejectedException;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.query.ContentTagAssignmentView;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLifecycleException;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposurePolicy;
import dev.persefonia.contentpublishing.application.publicview.ContentTagMutationFacts;

public final class ContentTagAssignmentService {
    public static final int MAX_TAGS = 12;

    private final ContentItemRepository contentItems;
    private final ContentTagVocabularyPort vocabulary;
    private final ContentCommandAuthorizationPolicy authorization;
    private final ContentPublicExposurePolicy exposurePolicy;

    public ContentTagAssignmentService(
            ContentItemRepository contentItems,
            ContentTagVocabularyPort vocabulary,
            ContentCommandAuthorizationPolicy authorization) {
        this(contentItems, vocabulary, authorization, new ContentPublicExposurePolicy());
    }

    public ContentTagAssignmentService(
            ContentItemRepository contentItems,
            ContentTagVocabularyPort vocabulary,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublicExposurePolicy exposurePolicy) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy");
    }

    public ContentTagAssignmentView view(ContentCommandActor actor, ContentId contentId) {
        authorization.requireOwner(actor, "content.tag.admin-view");
        ContentItem content = requiredContent(contentItems, contentId);
        return new ContentTagAssignmentView(
                contentId,
                vocabulary.findAssignableTags(),
                vocabulary.findByIds(content.tagIds()));
    }

    public void assign(AssignContentTagsCommand command) {
        assignWithFacts(command);
    }

    public ContentTagMutationFacts assignWithFacts(AssignContentTagsCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "content.tag.assign");
        ContentItem content = requiredContent(contentItems, command.contentId());

        Set<TagId> requested = new LinkedHashSet<>(command.requestedTagIds());
        if (requested.size() > MAX_TAGS) {
            throw new ContentTagAssignmentRejectedException(
                    ContentTagAssignmentRejectedException.Reason.TOO_MANY_TAGS,
                    "A content item may have at most " + MAX_TAGS + " tags.");
        }
        rejectIfNotEditable(content);

        Set<TagId> current = content.tagIds();
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

        try {
            content.replaceTags(requested, command.assignedAt());
        } catch (ContentLifecycleException exception) {
            throw notEditable(exception);
        }
        ContentItem saved = contentItems.save(content);
        return new ContentTagMutationFacts(
                saved.id(), saved.language(), exposurePolicy.snapshot(saved).listed(), current, requested,
                !current.equals(requested));
    }

    private static void rejectIfNotEditable(ContentItem content) {
        if (content.isArchived()) {
            throw notEditable(null);
        }
    }

    private static ContentTagAssignmentRejectedException notEditable(RuntimeException cause) {
        var rejection = new ContentTagAssignmentRejectedException(
                ContentTagAssignmentRejectedException.Reason.CONTENT_NOT_EDITABLE,
                "Tags cannot be changed for content that is not editable.");
        if (cause != null) {
            rejection.initCause(cause);
        }
        return rejection;
    }
}

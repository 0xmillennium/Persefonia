package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.ContentPublishResult;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.event.ContentPublished;
import dev.persefonia.contentpublishing.application.event.PublishedContentChanged;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.revision.CompleteContentSnapshot;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionMetadata;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposurePolicy;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFactsFactory;
import java.util.Objects;
import java.util.Optional;

public final class ContentPublishCommandHandler {
    private final ContentItemRepository contentItems;
    private final ContentRevisionRepository revisions;
    private final MarkdownRenderingService renderer;
    private final ContentCommandAuthorizationPolicy authorization;
    private final ContentPublishingEventPublisher events;
    private final ContentDiscoverabilityCoordinator discoverability;
    private final ContentPublicMutationFactsFactory publicFacts;

    public ContentPublishCommandHandler(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            MarkdownRenderingService renderer,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events,
            ContentDiscoverabilityCoordinator discoverability) {
        this(contentItems, revisions, renderer, authorization, events, discoverability,
                new ContentPublicMutationFactsFactory(new ContentPublicExposurePolicy(), new ContentPublicRouteFactory()));
    }

    public ContentPublishCommandHandler(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            MarkdownRenderingService renderer,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events,
            ContentDiscoverabilityCoordinator discoverability,
            ContentPublicMutationFactsFactory publicFacts) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.events = Objects.requireNonNull(events, "events");
        this.discoverability = Objects.requireNonNull(discoverability, "discoverability");
        this.publicFacts = Objects.requireNonNull(publicFacts, "publicFacts");
    }

    public ContentPublishResult publish(PublishContentCommand command) {
        authorization.requireOwner(command.actor(), "content.publish");
        ContentItem item = requiredContent(contentItems, command.contentId());
        var beforePublicState = publicFacts.capture(item);
        boolean alreadyPublished = item.isPublished();
        Optional<Slug> previousSlug = item.slug();
        ContentVisibility previousVisibility = item.visibility();
        var source = item.markdownSource()
                .orElseThrow(() -> new ContentCommandRejectedException("Content requires markdown source for publish"));
        var snapshot = renderer.render(source, command.requestedAt());
        item.publish(snapshot, command.requestedAt());
        ContentItem saved = contentItems.save(item);

        RevisionNumber revisionNumber = revisions.findLatestRevisionNumber(saved.id())
                .map(number -> RevisionNumber.of(number.value() + 1))
                .orElseGet(() -> RevisionNumber.of(1));
        ContentRevision revision = ContentRevision.publishSnapshot(
                ContentRevisionId.newId(),
                saved.id(),
                revisionNumber,
                CompleteContentSnapshot.of(
                        saved.title().orElseThrow(),
                        saved.slug().orElseThrow(),
                        saved.summary().orElseThrow(),
                        saved.markdownSource().orElseThrow(),
                        snapshot.renderedHtml(),
                        RevisionMetadata.from(saved.metadata())),
                command.actor().identityRef(),
                command.requestedAt(),
                command.changeNote());
        revisions.save(revision);
        discoverability.syncPublishedContent(saved, alreadyPublished, previousVisibility, previousSlug);

        if (alreadyPublished) {
            events.publish(new PublishedContentChanged(
                    saved.id(), saved.type(), saved.language(), command.actor().identityRef(),
                    command.requestedAt(), revisionNumber));
        } else {
            events.publish(new ContentPublished(
                    saved.id(), saved.type(), saved.language(), command.actor().identityRef(),
                    command.requestedAt(), saved.publishedAt().orElseThrow()));
        }
        return new ContentPublishResult(
                saved.id(), saved.status(), snapshot, revisionNumber, saved.publishedAt().orElseThrow(),
                publicFacts.between(saved.id(), beforePublicState, saved));
    }
}

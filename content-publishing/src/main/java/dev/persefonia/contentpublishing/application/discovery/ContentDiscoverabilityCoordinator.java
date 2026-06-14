package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.application.exception.ContentDiscoverySynchronizationException;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import java.util.Objects;
import java.util.Optional;

public final class ContentDiscoverabilityCoordinator {
    private final UpdateDiscoverableResourcePort updatePort;
    private final RemoveDiscoverableResourcePort removePort;
    private final CreateRedirectRulePort redirectPort;
    private final ContentDiscoveryProjectionFactory projectionFactory;
    private final ContentDiscoveryRedirectFactory redirectFactory;

    public ContentDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            CreateRedirectRulePort redirectPort,
            ContentDiscoveryProjectionFactory projectionFactory,
            ContentDiscoveryRedirectFactory redirectFactory) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.removePort = Objects.requireNonNull(removePort, "removePort");
        this.redirectPort = Objects.requireNonNull(redirectPort, "redirectPort");
        this.projectionFactory = Objects.requireNonNull(projectionFactory, "projectionFactory");
        this.redirectFactory = Objects.requireNonNull(redirectFactory, "redirectFactory");
    }

    public void syncPublishedContent(ContentItem saved, boolean wasPublished, ContentVisibility previousVisibility,
            Optional<Slug> previousSlug) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(previousVisibility, "previousVisibility");
        Objects.requireNonNull(previousSlug, "previousSlug");

        syncCurrentProjection(saved);
        redirectFactory.slugChangedRedirect(saved, wasPublished, previousVisibility, previousSlug)
                .ifPresent(command -> requireRedirectSuccess(redirectPort.create(command)));
    }

    public void syncContentUpdate(
            ContentItem saved,
            ContentStatus previousStatus,
            ContentVisibility previousVisibility,
            Optional<Slug> previousSlug) {
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(previousVisibility, "previousVisibility");
        Objects.requireNonNull(previousSlug, "previousSlug");

        syncCurrentProjection(saved);
        redirectFactory.slugChangedRedirect(
                        saved, previousStatus == ContentStatus.PUBLISHED, previousVisibility, previousSlug)
                .ifPresent(command -> requireRedirectSuccess(redirectPort.create(command)));
    }

    public void removeContent(ContentItem saved) {
        requireProjectionSuccess(removePort.remove(removeCommand(saved)));
    }

    private void syncCurrentProjection(ContentItem saved) {
        projectionFactory.projectionFor(saved)
                .ifPresentOrElse(
                        input -> requireProjectionSuccess(updatePort.update(input)),
                        () -> requireProjectionSuccess(removePort.remove(removeCommand(saved))));
    }

    private RemoveDiscoverableResourceCommand removeCommand(ContentItem item) {
        return projectionFactory.removeCommandFor(item);
    }

    private static void requireProjectionSuccess(DiscoverableResourceProjectionResult result) {
        if (result instanceof DiscoverableResourceProjectionResult.Rejected rejected) {
            throw new ContentDiscoverySynchronizationException(
                    "Discovery projection synchronization was rejected: " + rejected.reason());
        }
    }

    private static void requireRedirectSuccess(RedirectRuleCreationResult result) {
        if (result instanceof RedirectRuleCreationResult.Rejected rejected) {
            throw new ContentDiscoverySynchronizationException(
                    "Discovery redirect synchronization was rejected: " + rejected.reason());
        }
    }
}

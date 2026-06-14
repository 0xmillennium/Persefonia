package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import java.util.Objects;
import java.util.Optional;

public final class ContentDiscoveryRedirectFactory {
    private final ContentPublicRouteFactory routeFactory;

    public ContentDiscoveryRedirectFactory(ContentPublicRouteFactory routeFactory) {
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public Optional<CreateRedirectRuleCommand> slugChangedRedirect(
            ContentItem saved,
            boolean wasPublished,
            ContentVisibility previousVisibility,
            Optional<Slug> previousSlug) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(previousVisibility, "previousVisibility");
        Objects.requireNonNull(previousSlug, "previousSlug");

        if (!wasDirectUrlEligible(wasPublished, previousVisibility) || !saved.isDirectUrlEligible()) {
            return Optional.empty();
        }
        Optional<Slug> currentSlug = saved.slug();
        if (previousSlug.isEmpty() || currentSlug.isEmpty() || previousSlug.equals(currentSlug)) {
            return Optional.empty();
        }

        return Optional.of(new CreateRedirectRuleCommand(
                routeFactory.publicUrl(saved.type(), saved.language(), previousSlug.orElseThrow()),
                routeFactory.publicUrl(saved.type(), saved.language(), currentSlug.orElseThrow()),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.SLUG_CHANGED,
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(saved.id().value())));
    }

    private static boolean wasDirectUrlEligible(boolean wasPublished, ContentVisibility previousVisibility) {
        return wasPublished && previousVisibility != ContentVisibility.PRIVATE;
    }
}

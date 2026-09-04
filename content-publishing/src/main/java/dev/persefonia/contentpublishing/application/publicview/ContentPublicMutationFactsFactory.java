package dev.persefonia.contentpublishing.application.publicview;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;
import java.util.Optional;

public final class ContentPublicMutationFactsFactory {
    private final ContentPublicExposurePolicy exposurePolicy;
    private final ContentPublicRouteFactory routeFactory;

    public ContentPublicMutationFactsFactory(
            ContentPublicExposurePolicy exposurePolicy,
            ContentPublicRouteFactory routeFactory) {
        this.exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public State capture(ContentItem item) {
        Objects.requireNonNull(item, "item");
        Optional<PublicUrl> route = item.slug().map(slug -> routeFactory.publicUrl(item.type(), item.language(), slug));
        return new State(exposurePolicy.snapshot(item), route);
    }

    public ContentPublicMutationFacts between(ContentId contentId, State before, ContentItem after) {
        State current = capture(after);
        return new ContentPublicMutationFacts(
                contentId, before.exposure(), current.exposure(), before.route(), current.route());
    }

    public ContentPublicMutationFacts created(ContentItem item) {
        return between(item.id(), new State(ContentPublicExposureSnapshot.none(), Optional.empty()), item);
    }

    public record State(ContentPublicExposureSnapshot exposure, Optional<PublicUrl> route) {
        public State {
            Objects.requireNonNull(exposure, "exposure");
            route = Objects.requireNonNull(route, "route");
        }
    }
}

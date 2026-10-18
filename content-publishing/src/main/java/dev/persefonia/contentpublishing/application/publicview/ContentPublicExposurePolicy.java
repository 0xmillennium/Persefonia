package dev.persefonia.contentpublishing.application.publicview;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.util.Objects;

public final class ContentPublicExposurePolicy {
    public ContentPublicExposureSnapshot snapshot(ContentItem item) {
        Objects.requireNonNull(item, "item");
        return snapshot(item.status(), item.visibility(), item.type());
    }

    public ContentPublicExposureSnapshot snapshot(
            ContentStatus status, ContentVisibility visibility, ContentType type) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(type, "type");
        if (status != ContentStatus.PUBLISHED) {
            return ContentPublicExposureSnapshot.none();
        }
        boolean homepagePromotionEligible = switch (type) {
            case ARTICLE, NOTE, RESEARCH -> true;
            case PAGE -> false;
        };
        return switch (visibility) {
            case PRIVATE -> ContentPublicExposureSnapshot.none();
            case UNLISTED -> new ContentPublicExposureSnapshot(true, false, false, false, false);
            case PUBLIC -> new ContentPublicExposureSnapshot(
                    true, true, homepagePromotionEligible, true, type != ContentType.PAGE);
        };
    }
}

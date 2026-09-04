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
        if (status != ContentStatus.PUBLISHED || visibility == ContentVisibility.PRIVATE) {
            return ContentPublicExposureSnapshot.none();
        }
        if (visibility == ContentVisibility.UNLISTED) {
            return new ContentPublicExposureSnapshot(true, false, false, false);
        }
        return new ContentPublicExposureSnapshot(true, true, true, type != ContentType.PAGE);
    }
}

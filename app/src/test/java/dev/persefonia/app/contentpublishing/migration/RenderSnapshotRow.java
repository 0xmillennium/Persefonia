package dev.persefonia.app.contentpublishing.migration;

import java.time.OffsetDateTime;
import java.util.UUID;

record RenderSnapshotRow(
        UUID contentItemId,
        String renderedHtml,
        OffsetDateTime renderedAt,
        String rendererVersion,
        int readingTimeMinutes,
        boolean containsMermaid) {
    private static final OffsetDateTime RENDERED_AT = OffsetDateTime.parse("2026-06-11T09:00:00Z");

    static RenderSnapshotRow valid(UUID contentItemId) {
        return new RenderSnapshotRow(contentItemId, "<p>Rendered</p>", RENDERED_AT, "renderer-v1", 1, false);
    }

    RenderSnapshotRow withContentItemId(UUID value) {
        return new RenderSnapshotRow(value, renderedHtml, renderedAt, rendererVersion, readingTimeMinutes,
                containsMermaid);
    }

    RenderSnapshotRow withRenderedHtml(String value) {
        return new RenderSnapshotRow(contentItemId, value, renderedAt, rendererVersion, readingTimeMinutes,
                containsMermaid);
    }

    RenderSnapshotRow withRendererVersion(String value) {
        return new RenderSnapshotRow(contentItemId, renderedHtml, renderedAt, value, readingTimeMinutes,
                containsMermaid);
    }

    RenderSnapshotRow withReadingTimeMinutes(int value) {
        return new RenderSnapshotRow(contentItemId, renderedHtml, renderedAt, rendererVersion, value,
                containsMermaid);
    }
}

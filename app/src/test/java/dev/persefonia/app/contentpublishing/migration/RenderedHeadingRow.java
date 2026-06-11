package dev.persefonia.app.contentpublishing.migration;

import java.util.UUID;

record RenderedHeadingRow(
        UUID id,
        UUID contentItemId,
        int level,
        String text,
        String anchor,
        int position) {
    static RenderedHeadingRow valid(UUID contentItemId) {
        return new RenderedHeadingRow(UUID.randomUUID(), contentItemId, 2, "Heading", "heading", 1);
    }

    RenderedHeadingRow withContentItemId(UUID value) {
        return new RenderedHeadingRow(id, value, level, text, anchor, position);
    }

    RenderedHeadingRow withLevel(int value) {
        return new RenderedHeadingRow(id, contentItemId, value, text, anchor, position);
    }

    RenderedHeadingRow withText(String value) {
        return new RenderedHeadingRow(id, contentItemId, level, value, anchor, position);
    }

    RenderedHeadingRow withAnchor(String value) {
        return new RenderedHeadingRow(id, contentItemId, level, text, value, position);
    }

    RenderedHeadingRow withPosition(int value) {
        return new RenderedHeadingRow(id, contentItemId, level, text, anchor, value);
    }

    RenderedHeadingRow duplicateIdentity() {
        return new RenderedHeadingRow(UUID.randomUUID(), contentItemId, level, text, anchor, position);
    }
}

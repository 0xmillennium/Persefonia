package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record RenderedHeading(
        HeadingLevel level,
        HeadingText text,
        HeadingAnchor anchor,
        SortOrder position) {
    public RenderedHeading {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(position, "position");
    }

    public static RenderedHeading of(int level, String text, String anchor, int position) {
        return new RenderedHeading(
                HeadingLevel.of(level),
                HeadingText.of(text),
                HeadingAnchor.of(anchor),
                SortOrder.of(position));
    }
}

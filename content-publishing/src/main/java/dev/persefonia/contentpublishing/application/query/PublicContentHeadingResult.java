package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import dev.persefonia.contentpublishing.domain.content.HeadingLevel;
import dev.persefonia.contentpublishing.domain.content.HeadingText;
import dev.persefonia.contentpublishing.domain.content.SortOrder;
import java.util.Objects;

public record PublicContentHeadingResult(
        HeadingLevel level,
        HeadingText text,
        HeadingAnchor anchor,
        SortOrder position) {
    public PublicContentHeadingResult {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(position, "position");
    }
}

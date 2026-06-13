package dev.persefonia.webpublic.content;

import java.util.Objects;

public record PublicContentHeadingView(
        int level,
        String text,
        String anchor,
        int position) {
    public PublicContentHeadingView {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("level must be between 1 and 6");
        }
        text = Objects.requireNonNull(text, "text");
        anchor = Objects.requireNonNull(anchor, "anchor");
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
    }

    public String href() {
        return "#" + anchor;
    }
}

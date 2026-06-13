package dev.persefonia.webpublic.content;

import java.util.Objects;

public record PublicContentHeadingView(
        int level,
        String text,
        String anchor) {
    public PublicContentHeadingView {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("level must be between 1 and 6");
        }
        text = Objects.requireNonNull(text, "text");
        anchor = Objects.requireNonNull(anchor, "anchor");
    }
}

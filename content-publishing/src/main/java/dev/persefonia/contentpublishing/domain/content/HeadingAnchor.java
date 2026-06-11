package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.regex.Pattern;

public record HeadingAnchor(String value) {
    private static final Pattern URL_SAFE_ANCHOR = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");

    public HeadingAnchor {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ContentValidationException("heading anchor must not be blank");
        }
        if (!URL_SAFE_ANCHOR.matcher(value).matches()) {
            throw new ContentValidationException("heading anchor must be URL-safe");
        }
    }

    public static HeadingAnchor of(String value) {
        return new HeadingAnchor(value);
    }
}

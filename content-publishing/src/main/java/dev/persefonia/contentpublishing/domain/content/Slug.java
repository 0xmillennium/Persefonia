package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.regex.Pattern;

public record Slug(String value) {
    private static final Pattern CANONICAL_SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public Slug {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ContentValidationException("slug must not be blank");
        }
        if (!CANONICAL_SLUG.matcher(value).matches()) {
            throw new ContentValidationException("slug must be lowercase canonical slug");
        }
    }

    public static Slug ofCanonical(String value) {
        return new Slug(value);
    }

    public static Slug of(String value) {
        return ofCanonical(value);
    }
}

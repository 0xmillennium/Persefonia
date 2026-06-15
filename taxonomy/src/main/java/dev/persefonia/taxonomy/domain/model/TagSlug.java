package dev.persefonia.taxonomy.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record TagSlug(String value) {
    public static final int MAX_LENGTH = 100;
    private static final Pattern CANONICAL = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public TagSlug {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH) {
            throw new TagValidationException("tag slug must not exceed " + MAX_LENGTH + " characters");
        }
        if (!CANONICAL.matcher(value).matches()) {
            throw new TagValidationException("tag slug must be a lowercase URL-safe slug");
        }
    }

    public static TagSlug ofCanonical(String value) {
        return new TagSlug(value);
    }
}

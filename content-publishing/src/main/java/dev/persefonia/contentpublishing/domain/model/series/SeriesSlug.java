package dev.persefonia.contentpublishing.domain.model.series;

import java.util.Objects;
import java.util.regex.Pattern;

public record SeriesSlug(String value) {
    private static final Pattern CANONICAL_SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public SeriesSlug {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new SeriesValidationException("series slug must not be blank");
        }
        if (!CANONICAL_SLUG.matcher(value).matches()) {
            throw new SeriesValidationException("series slug must be lowercase canonical slug");
        }
    }

    public static SeriesSlug of(String value) {
        return new SeriesSlug(value);
    }
}

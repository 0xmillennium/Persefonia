package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.regex.Pattern;

public record ProjectSlug(String value) {
    private static final Pattern CANONICAL_SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public ProjectSlug {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("project slug must not be blank");
        }
        if (!CANONICAL_SLUG.matcher(value).matches()) {
            throw new ProjectValidationException("project slug must be lowercase canonical slug");
        }
    }

    public static ProjectSlug of(String value) {
        return new ProjectSlug(value);
    }
}

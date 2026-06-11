package dev.persefonia.webadmin.content;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentFormValidator {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public List<AdminContentFieldError> validate(AdminContentForm form, boolean create) {
        List<AdminContentFieldError> errors = new ArrayList<>();
        required(errors, "type", form.getType());
        required(errors, "language", form.getLanguage());
        required(errors, "visibility", form.getVisibility());
        enumValue(errors, "type", form.getType(), "ARTICLE", "NOTE", "RESEARCH", "PAGE");
        enumValue(errors, "language", form.getLanguage(), "TR", "EN");
        enumValue(errors, "visibility", form.getVisibility(), "PUBLIC", "UNLISTED", "PRIVATE");
        if (!create) {
            optionalPattern(errors, "slug", form.getSlug(), SLUG, "Use lowercase letters, numbers, and hyphens.");
            max(errors, "title", form.getTitle(), 200);
            max(errors, "summary", form.getSummary(), 500);
            max(errors, "metaTitle", form.getMetaTitle(), 200);
            max(errors, "metaDescription", form.getMetaDescription(), 500);
            max(errors, "ogTitle", form.getOgTitle(), 200);
            max(errors, "ogDescription", form.getOgDescription(), 500);
            uuid(errors, form.getOgImageAssetId());
        }
        return List.copyOf(errors);
    }

    private static void required(List<AdminContentFieldError> errors, String field, String value) {
        if (value.isBlank()) {
            errors.add(new AdminContentFieldError(field, "This field is required."));
        }
    }

    private static void enumValue(List<AdminContentFieldError> errors, String field, String value, String... allowed) {
        if (!value.isBlank() && java.util.Arrays.stream(allowed).noneMatch(value::equals)) {
            errors.add(new AdminContentFieldError(field, "Choose a valid value."));
        }
    }

    private static void optionalPattern(
            List<AdminContentFieldError> errors, String field, String value, Pattern pattern, String message) {
        if (!value.isBlank() && !pattern.matcher(value).matches()) {
            errors.add(new AdminContentFieldError(field, message));
        }
    }

    private static void max(List<AdminContentFieldError> errors, String field, String value, int maximum) {
        if (value.length() > maximum) {
            errors.add(new AdminContentFieldError(field, "Must not exceed " + maximum + " characters."));
        }
    }

    private static void uuid(List<AdminContentFieldError> errors, String value) {
        if (value.isBlank()) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            errors.add(new AdminContentFieldError("ogImageAssetId", "Must be a valid UUID."));
        }
    }
}

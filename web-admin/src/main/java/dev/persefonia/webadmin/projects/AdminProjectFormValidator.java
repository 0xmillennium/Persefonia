package dev.persefonia.webadmin.projects;

import dev.persefonia.profileportfolio.application.service.ExternalUrlPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AdminProjectFormValidator {
    public static final int MAX_TAGS = 12;
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "COMPLETED", "ARCHIVED", "EXPERIMENT");
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "UNLISTED", "PRIVATE");
    private static final Set<String> TECHNOLOGY_CATEGORIES = Set.of("LANGUAGE", "FRAMEWORK", "DATABASE", "INFRA", "TOOL", "LIBRARY");
    private static final Set<String> LINK_TYPES = Set.of("SOURCE", "DEMO", "DOCUMENTATION", "PAPER", "OTHER");

    public List<AdminProjectFieldError> validate(AdminProjectForm form, String defaultLanguage) {
        List<AdminProjectFieldError> errors = new ArrayList<>();
        if (!STATUSES.contains(form.getStatus())) {
            errors.add(new AdminProjectFieldError("status", "Choose a valid project status."));
        }
        if (!VISIBILITIES.contains(form.getVisibility())) {
            errors.add(new AdminProjectFieldError("visibility", "Choose a valid project visibility."));
        }
        if (!form.getSortOrder().isBlank()) {
            try {
                if (Integer.parseInt(form.getSortOrder().trim()) <= 0) {
                    errors.add(new AdminProjectFieldError("sortOrder", "Sort order must be positive."));
                }
            } catch (NumberFormatException exception) {
                errors.add(new AdminProjectFieldError("sortOrder", "Sort order must be a number."));
            }
        }
        validateLocalization(errors, "tr", form.isTrEnabled(), form.getTrSlug(), form.getTrTitle(), form.getTrSummary());
        validateLocalization(errors, "en", form.isEnEnabled(), form.getEnSlug(), form.getEnTitle(), form.getEnSummary());
        validateTechnologies(errors, form.getTechnologies());
        validateLinks(errors, form.getLinks());
        validateTags(errors, form.getTagIds());
        if ("PUBLIC".equals(form.getVisibility()) && !form.isTrEnabled() && !form.isEnEnabled()) {
            errors.add(new AdminProjectFieldError("visibility", "Public projects need at least one localization."));
        }
        if (form.isFeatured() && (("TR".equals(defaultLanguage) && !form.isTrEnabled())
                || ("EN".equals(defaultLanguage) && !form.isEnEnabled()))) {
            errors.add(new AdminProjectFieldError("featured", "Featured projects need the default-language localization."));
        }
        if (form.isFeatured() && !"PUBLIC".equals(form.getVisibility())) {
            errors.add(new AdminProjectFieldError("featured", "Featured projects must be public."));
        }
        if (form.isFeatured() && "ARCHIVED".equals(form.getStatus())) {
            errors.add(new AdminProjectFieldError("featured", "Archived projects cannot be featured."));
        }
        return errors;
    }

    private static void validateLocalization(
            List<AdminProjectFieldError> errors,
            String prefix,
            boolean enabled,
            String slug,
            String title,
            String summary) {
        if (!enabled) {
            return;
        }
        if (slug.isBlank()) errors.add(new AdminProjectFieldError(prefix + "Slug", "Slug is required."));
        else if (!SLUG.matcher(slug.trim()).matches()) errors.add(new AdminProjectFieldError(prefix + "Slug", "Slug must be a lowercase canonical slug."));
        if (title.isBlank()) errors.add(new AdminProjectFieldError(prefix + "Title", "Title is required."));
        if (summary.isBlank()) errors.add(new AdminProjectFieldError(prefix + "Summary", "Summary is required."));
    }

    private static void validateTechnologies(List<AdminProjectFieldError> errors, String value) {
        Set<String> seen = new HashSet<>();
        List<String> lines = nonBlankLines(value);
        for (int index = 0; index < lines.size(); index++) {
            String field = "technologies";
            String[] parts = split(lines.get(index), 2);
            int lineNumber = index + 1;
            if (parts.length != 2) {
                errors.add(new AdminProjectFieldError(field, "Technologies line " + lineNumber + " must use: Name | CATEGORY."));
                continue;
            }
            String name = parts[0].trim();
            String category = parts[1].trim();
            if (name.isBlank()) {
                errors.add(new AdminProjectFieldError(field, "Technologies line " + lineNumber + " needs a name."));
            }
            if (!TECHNOLOGY_CATEGORIES.contains(category)) {
                errors.add(new AdminProjectFieldError(field, "Technologies line " + lineNumber + " has an invalid category."));
            }
            String key = name.toLowerCase(Locale.ROOT) + "\n" + category;
            if (!name.isBlank() && TECHNOLOGY_CATEGORIES.contains(category) && !seen.add(key)) {
                errors.add(new AdminProjectFieldError(field, "Technologies line " + lineNumber + " duplicates an earlier technology/category."));
            }
        }
    }

    private static void validateLinks(List<AdminProjectFieldError> errors, String value) {
        List<String> lines = nonBlankLines(value);
        for (int index = 0; index < lines.size(); index++) {
            String field = "links";
            String[] parts = split(lines.get(index), 3);
            int lineNumber = index + 1;
            if (parts.length != 3) {
                errors.add(new AdminProjectFieldError(field, "Links line " + lineNumber + " must use: Label | URL | TYPE."));
                continue;
            }
            if (parts[0].trim().isBlank()) {
                errors.add(new AdminProjectFieldError(field, "Links line " + lineNumber + " needs a label."));
            }
            if (!ExternalUrlPolicy.accepts(parts[1].trim())) {
                errors.add(new AdminProjectFieldError(field, "Links line " + lineNumber + " must use a valid http or https URL."));
            }
            if (!LINK_TYPES.contains(parts[2].trim())) {
                errors.add(new AdminProjectFieldError(field, "Links line " + lineNumber + " has an invalid type."));
            }
        }
    }

    private static void validateTags(List<AdminProjectFieldError> errors, List<String> tagIds) {
        Set<String> unique = new HashSet<>();
        for (String tagId : tagIds) {
            if (tagId == null || tagId.isBlank()) {
                continue;
            }
            try {
                unique.add(UUID.fromString(tagId).toString());
            } catch (IllegalArgumentException exception) {
                errors.add(new AdminProjectFieldError("tagIds", "Selected tags include an invalid id."));
            }
        }
        if (unique.size() > MAX_TAGS) {
            errors.add(new AdminProjectFieldError("tagIds", "Projects may have at most " + MAX_TAGS + " tags."));
        }
    }

    private static List<String> nonBlankLines(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : value.lines().map(line -> line.trim()).filter(line -> !line.isBlank()).toList();
    }

    private static String[] split(String line, int expectedParts) {
        String[] parts = line.split("\\|", -1);
        return parts.length == expectedParts ? parts : new String[0];
    }
}

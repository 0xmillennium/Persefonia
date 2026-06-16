package dev.persefonia.webadmin.profile;

import dev.persefonia.profileportfolio.application.service.ExternalUrlPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdminPersonalProfileFormValidator {
    private static final Set<String> LANGUAGES = Set.of("TR", "EN");
    private static final int MAX_EXTERNAL_LINKS = 10;
    private static final int MAX_FOCUS_AREAS = 12;
    private static final int MAX_EDUCATION_SUMMARIES = 10;
    private static final int MAX_CURRENT_FOCUS_ITEMS = 12;

    public List<AdminPersonalProfileFieldError> validate(
            AdminPersonalProfileForm form,
            String defaultLanguage) {
        List<AdminPersonalProfileFieldError> errors = new ArrayList<>();
        if (form.getDisplayName().isBlank()) {
            errors.add(new AdminPersonalProfileFieldError("displayName", "Display name is required."));
        }
        if (!form.isTrEnabled() && !form.isEnEnabled()) {
            errors.add(new AdminPersonalProfileFieldError("localizations", "Enable at least one localization."));
        }
        if (!LANGUAGES.contains(defaultLanguage) || !enabled(form, defaultLanguage)) {
            errors.add(new AdminPersonalProfileFieldError(
                    "localizations",
                    "Enable the settings default-language localization (" + defaultLanguage + ")."));
        }
        validateLocalization(form, "TR", errors);
        validateLocalization(form, "EN", errors);
        validateExternalLinks(form.getExternalLinks(), errors);
        return List.copyOf(errors);
    }

    private static void validateLocalization(
            AdminPersonalProfileForm form,
            String language,
            List<AdminPersonalProfileFieldError> errors) {
        if (!enabled(form, language)) {
            return;
        }
        if (shortBio(form, language).isBlank()) {
            errors.add(new AdminPersonalProfileFieldError(field(language, "ShortBio"), language + " short bio is required."));
        }
        if (longBio(form, language).isBlank()) {
            errors.add(new AdminPersonalProfileFieldError(field(language, "LongBio"), language + " long bio is required."));
        }
        validateFocusAreas(technicalFocusAreas(form, language), field(language, "TechnicalFocusAreas"), errors);
        validateEducationSummaries(educationSummaries(form, language), field(language, "EducationSummaries"), errors);
        validateFocusItems(currentFocusItems(form, language), field(language, "CurrentFocusItems"), errors);
    }

    private static void validateExternalLinks(String value, List<AdminPersonalProfileFieldError> errors) {
        List<String> lines = lines(value);
        if (lines.size() > MAX_EXTERNAL_LINKS) {
            errors.add(new AdminPersonalProfileFieldError("externalLinks", "External links can include at most 10 lines."));
        }
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = split(lines.get(index));
            int lineNumber = index + 1;
            if (parts.length != 2) {
                errors.add(new AdminPersonalProfileFieldError(
                        "externalLinks",
                        "External links line " + lineNumber + " must be: Label | https://example.com"));
                continue;
            }
            if (parts[0].isBlank()) {
                errors.add(new AdminPersonalProfileFieldError(
                        "externalLinks", "External links line " + lineNumber + " requires a label."));
            }
            if (!ExternalUrlPolicy.accepts(parts[1])) {
                errors.add(new AdminPersonalProfileFieldError(
                        "externalLinks", "External links line " + lineNumber + " must use a valid http or https URL."));
            }
        }
    }

    private static void validateFocusAreas(
            String value,
            String field,
            List<AdminPersonalProfileFieldError> errors) {
        List<String> lines = lines(value);
        if (lines.size() > MAX_FOCUS_AREAS) {
            errors.add(new AdminPersonalProfileFieldError(field, "Technical focus areas can include at most 12 lines."));
        }
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = split(lines.get(index));
            if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
                errors.add(new AdminPersonalProfileFieldError(
                        field,
                        "Technical focus areas line " + (index + 1) + " must be: Name or Name | Description"));
            }
        }
    }

    private static void validateEducationSummaries(
            String value,
            String field,
            List<AdminPersonalProfileFieldError> errors) {
        List<String> lines = lines(value);
        if (lines.size() > MAX_EDUCATION_SUMMARIES) {
            errors.add(new AdminPersonalProfileFieldError(field, "Education summaries can include at most 10 lines."));
        }
        for (int index = 0; index < lines.size(); index++) {
            String[] parts = split(lines.get(index));
            if (parts.length < 2 || parts.length > 3 || parts[0].isBlank() || parts[1].isBlank()) {
                errors.add(new AdminPersonalProfileFieldError(
                        field,
                        "Education summaries line " + (index + 1)
                                + " must be: Institution | Program or Institution | Program | Description"));
            }
        }
    }

    private static void validateFocusItems(
            String value,
            String field,
            List<AdminPersonalProfileFieldError> errors) {
        List<String> lines = lines(value);
        if (lines.size() > MAX_CURRENT_FOCUS_ITEMS) {
            errors.add(new AdminPersonalProfileFieldError(field, "Current focus items can include at most 12 lines."));
        }
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                errors.add(new AdminPersonalProfileFieldError(
                        field, "Current focus items line " + (index + 1) + " must not be blank."));
            }
        }
    }

    static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    static String[] split(String line) {
        String[] raw = line.split("\\|", -1);
        String[] trimmed = new String[raw.length];
        for (int index = 0; index < raw.length; index++) {
            trimmed[index] = raw[index].trim();
        }
        return trimmed;
    }

    private static boolean enabled(AdminPersonalProfileForm form, String language) {
        return switch (language) {
            case "TR" -> form.isTrEnabled();
            case "EN" -> form.isEnEnabled();
            default -> false;
        };
    }

    private static String shortBio(AdminPersonalProfileForm form, String language) {
        return "TR".equals(language) ? form.getTrShortBio() : form.getEnShortBio();
    }

    private static String longBio(AdminPersonalProfileForm form, String language) {
        return "TR".equals(language) ? form.getTrLongBio() : form.getEnLongBio();
    }

    private static String technicalFocusAreas(AdminPersonalProfileForm form, String language) {
        return "TR".equals(language) ? form.getTrTechnicalFocusAreas() : form.getEnTechnicalFocusAreas();
    }

    private static String educationSummaries(AdminPersonalProfileForm form, String language) {
        return "TR".equals(language) ? form.getTrEducationSummaries() : form.getEnEducationSummaries();
    }

    private static String currentFocusItems(AdminPersonalProfileForm form, String language) {
        return "TR".equals(language) ? form.getTrCurrentFocusItems() : form.getEnCurrentFocusItems();
    }

    private static String field(String language, String suffix) {
        return language.toLowerCase(java.util.Locale.ROOT) + suffix;
    }
}

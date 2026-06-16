package dev.persefonia.webadmin.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdminSiteSettingsFormValidator {
    private static final Set<String> LANGUAGES = Set.of("TR", "EN");
    private static final Set<String> THEMES = Set.of("LIGHT", "DARK", "SYSTEM");

    public List<AdminSiteSettingsFieldError> validate(AdminSiteSettingsForm form) {
        List<AdminSiteSettingsFieldError> errors = new ArrayList<>();
        if (form.getSiteName().isBlank()) {
            errors.add(new AdminSiteSettingsFieldError("siteName", "Site name is required."));
        }
        if (!LANGUAGES.contains(form.getDefaultLanguage())) {
            errors.add(new AdminSiteSettingsFieldError("defaultLanguage", "Choose TR or EN."));
        }
        if (!form.isSupportedTr() && !form.isSupportedEn()) {
            errors.add(new AdminSiteSettingsFieldError("supportedLanguages", "Select at least one supported language."));
        } else if (LANGUAGES.contains(form.getDefaultLanguage()) && !isSupported(form, form.getDefaultLanguage())) {
            errors.add(new AdminSiteSettingsFieldError(
                    "defaultLanguage", "Default language must be one of the supported languages."));
        }
        if (!THEMES.contains(form.getDefaultTheme())) {
            errors.add(new AdminSiteSettingsFieldError("defaultTheme", "Choose LIGHT, DARK, or SYSTEM."));
        }
        positiveInteger(form.getFeaturedProjectLimit(), "featuredProjectLimit", "Featured project limit", errors);
        positiveInteger(form.getLatestWritingLimit(), "latestWritingLimit", "Latest writing limit", errors);
        return List.copyOf(errors);
    }

    int featuredProjectLimit(AdminSiteSettingsForm form) {
        return Integer.parseInt(form.getFeaturedProjectLimit());
    }

    int latestWritingLimit(AdminSiteSettingsForm form) {
        return Integer.parseInt(form.getLatestWritingLimit());
    }

    private static void positiveInteger(
            String value,
            String field,
            String label,
            List<AdminSiteSettingsFieldError> errors) {
        try {
            if (Integer.parseInt(value) <= 0) {
                errors.add(new AdminSiteSettingsFieldError(field, label + " must be positive."));
            }
        } catch (NumberFormatException exception) {
            errors.add(new AdminSiteSettingsFieldError(field, label + " must be a whole number."));
        }
    }

    private static boolean isSupported(AdminSiteSettingsForm form, String language) {
        return switch (language) {
            case "TR" -> form.isSupportedTr();
            case "EN" -> form.isSupportedEn();
            default -> false;
        };
    }
}

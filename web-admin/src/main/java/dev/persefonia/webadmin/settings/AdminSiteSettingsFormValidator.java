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
        if (!THEMES.contains(form.getStandardPublicTheme())) {
            errors.add(new AdminSiteSettingsFieldError("standardPublicTheme", "Choose LIGHT, DARK, or SYSTEM."));
        }
        promotionLimit(form.getFeaturedProjectLimit(), "featuredProjectLimit", "Featured project limit", errors);
        promotionLimit(form.getLatestWritingLimit(), "latestWritingLimit", "Latest writing limit", errors);
        promotionLimit(form.getResearchHighlightLimit(), "researchHighlightLimit", "Research highlight limit", errors);
        return List.copyOf(errors);
    }

    int featuredProjectLimit(AdminSiteSettingsForm form) {
        return Integer.parseInt(form.getFeaturedProjectLimit());
    }

    int latestWritingLimit(AdminSiteSettingsForm form) {
        return Integer.parseInt(form.getLatestWritingLimit());
    }

    int researchHighlightLimit(AdminSiteSettingsForm form) {
        return Integer.parseInt(form.getResearchHighlightLimit());
    }

    private static void promotionLimit(
            String value,
            String field,
            String label,
            List<AdminSiteSettingsFieldError> errors) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 3) {
                errors.add(new AdminSiteSettingsFieldError(field, label + " must be between 1 and 3."));
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

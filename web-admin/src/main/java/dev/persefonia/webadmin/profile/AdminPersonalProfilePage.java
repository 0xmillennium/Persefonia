package dev.persefonia.webadmin.profile;

import java.util.List;
import java.util.Objects;

public record AdminPersonalProfilePage(
        AdminPersonalProfilePageChrome chrome,
        AdminPersonalProfileForm form,
        String defaultLanguage,
        boolean onboarding,
        List<AdminPersonalProfileFieldError> fieldErrors,
        List<String> globalErrors,
        String successMessage) {
    public AdminPersonalProfilePage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

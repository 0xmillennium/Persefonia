package dev.persefonia.webadmin.settings;

import java.util.List;
import java.util.Objects;

public record AdminSiteSettingsPage(
        AdminSiteSettingsPageChrome chrome,
        AdminSiteSettingsForm form,
        List<AdminSiteSettingsFieldError> fieldErrors,
        List<String> globalErrors,
        String successMessage) {
    public AdminSiteSettingsPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

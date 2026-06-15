package dev.persefonia.webadmin.discovery;

import java.util.List;
import java.util.Objects;

public record AdminRedirectPage(
        AdminRedirectPageChrome chrome,
        AdminRedirectForm form,
        List<AdminRedirectFieldError> fieldErrors,
        List<String> globalErrors,
        List<AdminRedirectRuleView> rules,
        String successMessage) {
    public AdminRedirectPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

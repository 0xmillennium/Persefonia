package dev.persefonia.webadmin.taxonomy;

import java.util.List;
import java.util.Objects;

public record AdminTagFormPage(
        AdminTagPageChrome chrome,
        AdminTagForm form,
        String heading,
        String action,
        String status,
        String archiveAction,
        List<AdminTagFieldError> fieldErrors,
        List<String> globalErrors) {
    public AdminTagFormPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(action, "action");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

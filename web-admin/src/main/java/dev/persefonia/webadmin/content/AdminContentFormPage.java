package dev.persefonia.webadmin.content;

import java.util.List;
import java.util.Objects;

public record AdminContentFormPage(
        AdminContentPageChrome chrome,
        String heading,
        String action,
        boolean create,
        AdminContentForm form,
        List<AdminContentFieldError> fieldErrors,
        List<String> globalErrors,
        String status,
        String previewLink,
        String revisionsLink,
        boolean editable,
        String readOnlyMessage,
        AdminContentLifecycleActionView lifecycleActions,
        String successMessage) {
    public AdminContentFormPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
        Objects.requireNonNull(lifecycleActions, "lifecycleActions");
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

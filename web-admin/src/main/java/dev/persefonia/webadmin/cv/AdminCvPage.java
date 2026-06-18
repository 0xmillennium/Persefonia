package dev.persefonia.webadmin.cv;

import dev.persefonia.profileportfolio.application.query.ActiveCvAdminPageData;
import java.util.List;
import java.util.Objects;

public record AdminCvPage(
        AdminCvPageChrome chrome,
        ActiveCvAdminPageData data,
        AdminCvForm form,
        List<AdminCvFieldError> fieldErrors,
        List<String> globalErrors,
        String successMessage) {
    public AdminCvPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

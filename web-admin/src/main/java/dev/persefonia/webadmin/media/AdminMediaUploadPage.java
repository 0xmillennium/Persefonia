package dev.persefonia.webadmin.media;

import java.util.List;
import java.util.Objects;

public record AdminMediaUploadPage(
        AdminMediaPageChrome chrome,
        AdminMediaUploadForm form,
        List<AdminMediaFieldError> fieldErrors,
        List<String> globalErrors) {
    public AdminMediaUploadPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

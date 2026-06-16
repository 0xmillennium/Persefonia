package dev.persefonia.webadmin.series;

import dev.persefonia.contentpublishing.application.query.SeriesEditView;
import java.util.List;
import java.util.Objects;

public record AdminSeriesFormPage(
        AdminSeriesPageChrome chrome,
        AdminSeriesForm form,
        String heading,
        String action,
        SeriesEditView series,
        String archiveAction,
        List<AdminSeriesFieldError> fieldErrors,
        List<String> globalErrors,
        String successMessage) {
    public AdminSeriesFormPage {
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

    public boolean editing() {
        return series != null;
    }
}

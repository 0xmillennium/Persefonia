package dev.persefonia.webadmin.projects;

import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.query.AdminProjectTagView;
import java.util.List;

public record AdminProjectFormPage(
        AdminProjectPageChrome chrome,
        AdminProjectForm form,
        String heading,
        String action,
        String defaultLanguage,
        List<ProjectTagOption> assignableTags,
        List<AdminProjectTagView> assignedTags,
        List<AdminProjectFieldError> fieldErrors,
        List<String> globalErrors,
        String successMessage) {
    public AdminProjectFormPage {
        assignableTags = List.copyOf(assignableTags);
        assignedTags = List.copyOf(assignedTags);
        fieldErrors = List.copyOf(fieldErrors);
        globalErrors = List.copyOf(globalErrors);
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

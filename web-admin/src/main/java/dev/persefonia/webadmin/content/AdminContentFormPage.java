package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.query.ContentTagAssignmentView;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        String successMessage,
        ContentTagAssignmentView tagAssignment,
        Set<String> selectedTagIds,
        List<String> tagAssignmentErrors,
        String tagAssignmentSuccessMessage) {
    public AdminContentFormPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
        Objects.requireNonNull(lifecycleActions, "lifecycleActions");
        selectedTagIds = Set.copyOf(Objects.requireNonNull(selectedTagIds, "selectedTagIds"));
        tagAssignmentErrors = List.copyOf(Objects.requireNonNull(tagAssignmentErrors, "tagAssignmentErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }

    public boolean hasTagAssignmentErrors() {
        return !tagAssignmentErrors.isEmpty();
    }
}

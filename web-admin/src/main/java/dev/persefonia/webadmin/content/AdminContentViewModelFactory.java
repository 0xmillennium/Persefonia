package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.query.AdminContentEditResult;
import dev.persefonia.contentpublishing.application.query.AdminContentListItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentViewModelFactory {
    public AdminContentListPage list(AdminContentPageChrome chrome, List<AdminContentListItem> items) {
        return new AdminContentListPage(chrome, items.stream().map(this::listItem).toList());
    }

    public AdminContentFormPage create(AdminContentPageChrome chrome, AdminContentForm form) {
        return formPage(chrome, "Create draft", "/admin/content", true, form, List.of(), List.of(), null, null, null);
    }

    public AdminContentFormPage edit(
            AdminContentPageChrome chrome, AdminContentEditResult result, AdminContentForm form, String successMessage) {
        String id = result.contentId().value().toString();
        return formPage(
                chrome,
                "Edit content",
                "/admin/content/" + id,
                false,
                form,
                List.of(),
                List.of(),
                result.status().name(),
                result.markdownSource().isPresent() ? "/admin/content/" + id + "/preview" : null,
                successMessage);
    }

    public AdminContentFormPage editSubmission(AdminContentPageChrome chrome, String contentId, AdminContentForm form) {
        return formPage(
                chrome,
                "Edit content",
                "/admin/content/" + contentId,
                false,
                form,
                List.of(),
                List.of(),
                null,
                "/admin/content/" + contentId + "/preview",
                null);
    }

    public AdminContentFormPage withErrors(
            AdminContentFormPage page, List<AdminContentFieldError> fieldErrors, List<String> globalErrors) {
        return formPage(
                page.chrome(), page.heading(), page.action(), page.create(), page.form(), fieldErrors, globalErrors,
                page.status(), page.previewLink(), null);
    }

    private AdminContentListItemView listItem(AdminContentListItem item) {
        String id = item.contentId().value().toString();
        return new AdminContentListItemView(
                id,
                item.type().name(),
                item.language().name(),
                item.status().name(),
                item.visibility().name(),
                item.slug().orElse("Not set"),
                item.title().orElse("Untitled"),
                item.updatedAt().toString(),
                "/admin/content/" + id + "/edit",
                item.previewAvailable() ? "/admin/content/" + id + "/preview" : null);
    }

    private static AdminContentFormPage formPage(
            AdminContentPageChrome chrome,
            String heading,
            String action,
            boolean create,
            AdminContentForm form,
            List<AdminContentFieldError> fieldErrors,
            List<String> globalErrors,
            String status,
            String previewLink,
            String successMessage) {
        return new AdminContentFormPage(
                chrome, heading, action, create, form, fieldErrors, globalErrors, status, previewLink, successMessage);
    }
}

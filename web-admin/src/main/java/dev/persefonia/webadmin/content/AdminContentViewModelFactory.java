package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.query.AdminContentEditResult;
import dev.persefonia.contentpublishing.application.query.AdminContentListItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentViewModelFactory {
    public AdminContentListPage list(
            AdminContentPageChrome chrome, List<AdminContentListItem> items, String successMessage) {
        return new AdminContentListPage(chrome, items.stream().map(this::listItem).toList(), successMessage);
    }

    public AdminContentFormPage create(AdminContentPageChrome chrome, AdminContentForm form) {
        return formPage(
                chrome, "Create draft", "/admin/content", true, form, List.of(), List.of(), null, null, null,
                true, null, AdminContentLifecycleActionView.none(), null);
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
                "/admin/content/" + id + "/revisions",
                editable(result.status()),
                readOnlyMessage(result.status()),
                lifecycleActions(id, result.status()),
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
                "/admin/content/" + contentId + "/revisions",
                true,
                null,
                AdminContentLifecycleActionView.none(),
                null);
    }

    public AdminContentFormPage withErrors(
            AdminContentFormPage page, List<AdminContentFieldError> fieldErrors, List<String> globalErrors) {
        return formPage(
                page.chrome(), page.heading(), page.action(), page.create(), page.form(), fieldErrors, globalErrors,
                page.status(), page.previewLink(), page.revisionsLink(), page.editable(), page.readOnlyMessage(),
                page.lifecycleActions(), null);
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
            String revisionsLink,
            boolean editable,
            String readOnlyMessage,
            AdminContentLifecycleActionView lifecycleActions,
            String successMessage) {
        return new AdminContentFormPage(
                chrome, heading, action, create, form, fieldErrors, globalErrors, status, previewLink,
                revisionsLink, editable, readOnlyMessage, lifecycleActions, successMessage);
    }

    private static boolean editable(ContentStatus status) {
        return status == ContentStatus.DRAFT || status == ContentStatus.UNPUBLISHED;
    }

    private static String readOnlyMessage(ContentStatus status) {
        return switch (status) {
            case PUBLISHED -> "Published content is read-only. Unpublish it before editing.";
            case ARCHIVED -> "Archived content is read-only.";
            default -> null;
        };
    }

    private static AdminContentLifecycleActionView lifecycleActions(String id, ContentStatus status) {
        String base = "/admin/content/" + id;
        return switch (status) {
            case DRAFT, UNPUBLISHED -> new AdminContentLifecycleActionView(
                    base + "/publish", null, base + "/archive");
            case PUBLISHED -> new AdminContentLifecycleActionView(
                    null, base + "/unpublish", base + "/archive");
            case ARCHIVED -> AdminContentLifecycleActionView.none();
        };
    }
}

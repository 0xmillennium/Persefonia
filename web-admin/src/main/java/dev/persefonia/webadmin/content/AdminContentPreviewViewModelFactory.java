package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.command.ContentPreviewResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentPreviewViewModelFactory {
    public AdminContentPreviewPage success(AdminContentPageChrome chrome, ContentPreviewResult result) {
        String id = result.contentId().value().toString();
        return new AdminContentPreviewPage(
                chrome,
                "Content preview",
                "/admin/content/" + id + "/edit",
                result.snapshot().renderedHtml().value(),
                result.snapshot().containsMermaid(),
                List.of());
    }

    public AdminContentPreviewPage error(AdminContentPageChrome chrome, String contentId, String message) {
        return new AdminContentPreviewPage(
                chrome,
                "Content preview unavailable",
                "/admin/content/" + contentId + "/edit",
                null,
                false,
                List.of(message));
    }
}

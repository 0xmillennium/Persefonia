package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.query.ContentRevisionHistoryItem;
import dev.persefonia.contentpublishing.application.query.ContentRevisionHistoryResult;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentRevisionViewModelFactory {
    public AdminContentRevisionListPage list(
            AdminContentPageChrome chrome, ContentRevisionHistoryResult history) {
        String id = history.contentId().value().toString();
        return new AdminContentRevisionListPage(
                chrome,
                history.contentTitle().orElse("Untitled"),
                history.contentStatus(),
                "/admin/content/" + id + "/edit",
                history.revisions().stream().map(this::item).toList());
    }

    private AdminContentRevisionListItemView item(ContentRevisionHistoryItem item) {
        return new AdminContentRevisionListItemView(
                Integer.toString(item.revisionNumber()),
                item.revisionType(),
                item.title(),
                item.slug(),
                item.createdBy(),
                item.createdAt().toString(),
                item.changeNote().orElse(null),
                item.renderedHtmlPresent());
    }
}

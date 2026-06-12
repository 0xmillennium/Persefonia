package dev.persefonia.webadmin.content;

import java.util.List;
import java.util.Objects;

public record AdminContentRevisionListPage(
        AdminContentPageChrome chrome,
        String contentTitle,
        String contentStatus,
        String editLink,
        List<AdminContentRevisionListItemView> revisions) {
    public AdminContentRevisionListPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(contentTitle, "contentTitle");
        Objects.requireNonNull(contentStatus, "contentStatus");
        Objects.requireNonNull(editLink, "editLink");
        revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions"));
    }
}

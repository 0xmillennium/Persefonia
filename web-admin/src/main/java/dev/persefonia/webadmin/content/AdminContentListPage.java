package dev.persefonia.webadmin.content;

import java.util.List;
import java.util.Objects;

public record AdminContentListPage(AdminContentPageChrome chrome, List<AdminContentListItemView> items) {
    public AdminContentListPage {
        Objects.requireNonNull(chrome, "chrome");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}

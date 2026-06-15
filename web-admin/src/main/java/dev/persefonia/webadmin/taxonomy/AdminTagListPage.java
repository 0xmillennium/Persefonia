package dev.persefonia.webadmin.taxonomy;

import dev.persefonia.taxonomy.application.query.TagListItem;
import java.util.List;
import java.util.Objects;

public record AdminTagListPage(AdminTagPageChrome chrome, List<TagListItem> tags, String successMessage) {
    public AdminTagListPage {
        Objects.requireNonNull(chrome, "chrome");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}

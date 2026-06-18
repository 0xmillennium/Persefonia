package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.admin.MediaAdminAssetListItem;
import java.util.List;
import java.util.Objects;

public record AdminMediaListPage(
        AdminMediaPageChrome chrome,
        List<MediaAdminAssetListItem> assets,
        AdminMediaFlashMessage flashMessage) {
    public AdminMediaListPage {
        Objects.requireNonNull(chrome, "chrome");
        assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
    }
}

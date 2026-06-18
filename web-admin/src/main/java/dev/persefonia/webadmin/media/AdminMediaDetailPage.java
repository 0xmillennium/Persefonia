package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import java.util.List;
import java.util.Objects;

public record AdminMediaDetailPage(
        AdminMediaPageChrome chrome,
        MediaAdminAssetDetails asset,
        AdminMediaMetadataForm form,
        List<AdminMediaFieldError> fieldErrors,
        List<String> globalErrors,
        AdminMediaFlashMessage flashMessage) {
    public AdminMediaDetailPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(form, "form");
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !globalErrors.isEmpty();
    }
}

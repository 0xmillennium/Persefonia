package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public final class AdminMediaFormValidator {
    public List<AdminMediaFieldError> validateUpload(AdminMediaUploadForm form) {
        List<AdminMediaFieldError> errors = new ArrayList<>();
        MultipartFile file = form == null ? null : form.getFile();
        if (file == null || file.isEmpty()) {
            errors.add(new AdminMediaFieldError("file", "Choose a JPEG, PNG, or PDF file."));
        } else if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            errors.add(new AdminMediaFieldError("file", "The uploaded file must have a filename."));
        }
        return List.copyOf(errors);
    }

    public List<AdminMediaFieldError> validateMetadata(
            AdminMediaMetadataForm form,
            MediaAdminAssetDetails asset) {
        List<AdminMediaFieldError> errors = new ArrayList<>();
        try {
            AssetVisibility.valueOf(nullToBlank(form.getVisibility()).trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            errors.add(new AdminMediaFieldError("visibility", "Choose PRIVATE or PUBLIC."));
        }
        if (asset.summary().kind() != AssetKind.IMAGE && form.isDecorative()) {
            errors.add(new AdminMediaFieldError("decorative", "Decorative applies to images only."));
        }
        if (asset.summary().kind() != AssetKind.IMAGE && !nullToBlank(form.getAltText()).isBlank()) {
            errors.add(new AdminMediaFieldError("altText", "Alt text applies to images only."));
        }
        return List.copyOf(errors);
    }

    static AssetVisibility parseVisibility(String value) {
        return AssetVisibility.valueOf(nullToBlank(value).trim().toUpperCase());
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}

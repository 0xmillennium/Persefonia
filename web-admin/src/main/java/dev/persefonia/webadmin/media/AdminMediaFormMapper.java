package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.application.upload.UploadByteSource;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public final class AdminMediaFormMapper {
    public AdminUploadAssetCommand toUploadCommand(MediaCommandActor actor, AdminMediaUploadForm form) {
        MultipartFile file = form.getFile();
        String originalFilename = file.getOriginalFilename();
        return new AdminUploadAssetCommand(
                actor,
                originalFilename,
                file.getContentType(),
                extension(originalFilename),
                file.getSize(),
                byteSource(file));
    }

    public UpdateAssetMetadataCommand toMetadataCommand(
            MediaCommandActor actor,
            AssetId assetId,
            AdminMediaMetadataForm form) {
        return new UpdateAssetMetadataCommand(
                actor,
                assetId,
                AdminMediaFormValidator.parseVisibility(form.getVisibility()),
                blankToNull(form.getAltText()),
                form.isDecorative());
    }

    private static UploadByteSource byteSource(MultipartFile file) {
        return () -> {
            try {
                return file.getInputStream();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String clean = filename.replace('\\', '/');
        String leaf = clean.substring(clean.lastIndexOf('/') + 1);
        int dot = leaf.lastIndexOf('.');
        if (dot < 0 || dot == leaf.length() - 1) {
            return "";
        }
        return leaf.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

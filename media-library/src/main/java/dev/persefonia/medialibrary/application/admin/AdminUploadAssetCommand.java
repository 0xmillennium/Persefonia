package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.application.upload.UploadByteSource;
import java.util.Objects;

public record AdminUploadAssetCommand(
        MediaCommandActor actor,
        String originalFilename,
        String declaredContentType,
        String declaredExtension,
        long declaredSize,
        UploadByteSource byteSource) {
    public AdminUploadAssetCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(byteSource, "byteSource");
    }
}

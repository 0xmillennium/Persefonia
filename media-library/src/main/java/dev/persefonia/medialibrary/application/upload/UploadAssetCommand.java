package dev.persefonia.medialibrary.application.upload;

import java.util.Objects;

public record UploadAssetCommand(
        String originalFilename,
        String declaredContentType,
        String declaredExtension,
        long declaredSize,
        UploadByteSource byteSource) {
    public UploadAssetCommand {
        Objects.requireNonNull(byteSource, "byteSource");
    }
}

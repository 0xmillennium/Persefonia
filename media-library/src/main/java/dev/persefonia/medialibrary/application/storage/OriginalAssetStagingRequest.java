package dev.persefonia.medialibrary.application.storage;

import dev.persefonia.medialibrary.application.upload.UploadByteSource;
import java.util.Objects;

public record OriginalAssetStagingRequest(UploadByteSource byteSource, long maximumBytesToStage) {
    public OriginalAssetStagingRequest {
        Objects.requireNonNull(byteSource, "byteSource");
        if (maximumBytesToStage <= 0) {
            throw new IllegalArgumentException("maximumBytesToStage must be positive");
        }
    }
}

package dev.persefonia.medialibrary.application.storage;

public record StagedAssetObject(String stagingKey, long sizeBytes) {
    public StagedAssetObject {
        if (stagingKey == null || stagingKey.isBlank()) {
            throw new IllegalArgumentException("stagingKey must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}

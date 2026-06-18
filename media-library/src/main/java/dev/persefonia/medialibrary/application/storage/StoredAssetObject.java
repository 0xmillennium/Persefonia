package dev.persefonia.medialibrary.application.storage;

public record StoredAssetObject(String logicalPath) {
    public StoredAssetObject {
        if (logicalPath == null || logicalPath.isBlank()) {
            throw new IllegalArgumentException("logicalPath must not be blank");
        }
    }
}

package dev.persefonia.medialibrary.application.storage;

public record FinalAssetStorageKey(String value) {
    public FinalAssetStorageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}

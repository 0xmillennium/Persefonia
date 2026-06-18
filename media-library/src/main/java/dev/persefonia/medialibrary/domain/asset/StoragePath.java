package dev.persefonia.medialibrary.domain.asset;

public record StoragePath(String value) {
    public StoragePath {
        AssetValues.nonBlank(value, "storage path");
        if (value.indexOf('\0') >= 0 || value.contains("..") || value.startsWith("/") || value.contains("\\")) {
            throw new AssetValidationException("storage path contains unsafe metadata");
        }
    }

    public static StoragePath of(String value) {
        return new StoragePath(value);
    }
}

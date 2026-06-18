package dev.persefonia.medialibrary.domain.asset;

public record StoredFilename(String value) {
    public StoredFilename {
        AssetValues.nonBlank(value, "stored filename");
    }

    public static StoredFilename of(String value) {
        return new StoredFilename(value);
    }
}

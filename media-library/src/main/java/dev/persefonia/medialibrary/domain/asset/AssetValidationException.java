package dev.persefonia.medialibrary.domain.asset;

public final class AssetValidationException extends IllegalArgumentException {
    public AssetValidationException(String message) {
        super(message);
    }
}

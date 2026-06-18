package dev.persefonia.medialibrary.application.upload;

import java.util.Optional;

public enum DetectedMediaType {
    JPEG(AllowedMediaType.JPEG),
    PNG(AllowedMediaType.PNG),
    PDF(AllowedMediaType.PDF),
    UNKNOWN(null);

    private final AllowedMediaType allowedMediaType;

    DetectedMediaType(AllowedMediaType allowedMediaType) {
        this.allowedMediaType = allowedMediaType;
    }

    public Optional<AllowedMediaType> allowedMediaType() {
        return Optional.ofNullable(allowedMediaType);
    }
}

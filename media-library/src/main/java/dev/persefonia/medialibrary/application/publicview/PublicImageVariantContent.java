package dev.persefonia.medialibrary.application.publicview;

import java.io.InputStream;
import java.util.Objects;

public record PublicImageVariantContent(
        InputStream inputStream,
        String contentType,
        long contentLength) {
    public PublicImageVariantContent {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(contentType, "contentType");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
    }
}

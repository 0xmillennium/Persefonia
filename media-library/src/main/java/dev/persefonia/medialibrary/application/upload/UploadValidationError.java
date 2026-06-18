package dev.persefonia.medialibrary.application.upload;

import java.util.Objects;

public record UploadValidationError(UploadValidationErrorCode code, String message) {
    public UploadValidationError {
        Objects.requireNonNull(code, "code");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}

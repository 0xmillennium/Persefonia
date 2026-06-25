package dev.persefonia.webpublic.contact;

import java.util.Map;
import java.util.Objects;

public record PublicContactSubmissionResult(Status status, Map<String, String> fieldErrors) {
    public PublicContactSubmissionResult {
        Objects.requireNonNull(status, "status must not be null");
        fieldErrors = Map.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors must not be null"));
    }

    public static PublicContactSubmissionResult success() {
        return new PublicContactSubmissionResult(Status.SUCCESS, Map.of());
    }

    public static PublicContactSubmissionResult invalid(Map<String, String> fieldErrors) {
        return new PublicContactSubmissionResult(Status.VALIDATION_FAILED, fieldErrors);
    }

    public static PublicContactSubmissionResult rateLimited() {
        return new PublicContactSubmissionResult(Status.RATE_LIMITED, Map.of());
    }

    public static PublicContactSubmissionResult temporarilyUnavailable() {
        return new PublicContactSubmissionResult(Status.TEMPORARILY_UNAVAILABLE, Map.of());
    }

    public enum Status {
        SUCCESS,
        VALIDATION_FAILED,
        RATE_LIMITED,
        TEMPORARILY_UNAVAILABLE
    }
}

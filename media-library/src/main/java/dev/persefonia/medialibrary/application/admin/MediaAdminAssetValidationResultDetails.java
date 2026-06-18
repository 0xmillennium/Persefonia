package dev.persefonia.medialibrary.application.admin;

import java.time.Instant;
import java.util.Objects;

public record MediaAdminAssetValidationResultDetails(
        String rule,
        String status,
        String message,
        Instant checkedAt) {
    public MediaAdminAssetValidationResultDetails {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}

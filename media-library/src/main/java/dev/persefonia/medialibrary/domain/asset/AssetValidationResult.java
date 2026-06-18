package dev.persefonia.medialibrary.domain.asset;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AssetValidationResult(
        AssetValidationResultId id,
        ValidationRuleName rule,
        ValidationStatus status,
        ValidationMessage message,
        Instant checkedAt) {
    public AssetValidationResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }

    public Optional<ValidationMessage> messageOptional() {
        return Optional.ofNullable(message);
    }
}

package dev.persefonia.profileportfolio.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveCvUpdateResult(
        boolean updated,
        UUID profileId,
        Instant updatedAt,
        long version,
        List<ActiveCvCommandError> errors) {
    public ActiveCvUpdateResult {
        errors = List.copyOf(errors);
    }

    public static ActiveCvUpdateResult rejected(List<ActiveCvCommandError> errors) {
        return new ActiveCvUpdateResult(false, null, null, -1, errors);
    }
}

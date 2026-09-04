package dev.persefonia.platformoperations.application.recovery;

import dev.persefonia.platformoperations.application.operations.MigrationStatusSummary;
import dev.persefonia.platformoperations.application.operations.OperationsComponentStatus;
import java.util.Objects;

public record RecoveryVerificationContext(
        ApplicationReleaseInfo release,
        MigrationStatusSummary migrations,
        OperationsComponentStatus mediaStorage) {
    public RecoveryVerificationContext {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(migrations, "migrations");
        Objects.requireNonNull(mediaStorage, "mediaStorage");
    }
}

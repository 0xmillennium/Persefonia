package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.application.recovery.RecoveryVerificationContext;
import dev.persefonia.platformoperations.application.recovery.RecoveryVerificationReport;
import java.util.Objects;

public record AdminOperationsRecoveryPage(
        AdminOperationsPageChrome chrome,
        RecoveryVerificationContext context,
        RecoveryVerificationReport report) {
    public AdminOperationsRecoveryPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(context, "context");
    }
}

package dev.persefonia.platformoperations.application.recovery;

public interface RecoveryVerificationQueryPort {
    RecoveryVerificationContext context();
    RecoveryVerificationReport verify();
}

package dev.persefonia.platformoperations.application.operations;

public interface CacheInvalidationRecoveryGateway {
    CacheRecoveryCommandResult requestInitialExecution(CacheInvalidationRecoveryCommand command);
    CacheRecoveryCommandResult requestRetry(CacheInvalidationRecoveryCommand command);
    CacheRecoveryCommandResult requestStrandedResume(CacheInvalidationRecoveryCommand command);
}

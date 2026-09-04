package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.operations.*;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class CacheInvalidationRecoveryCoordinator implements CacheInvalidationRecoveryGateway {
    private final RecoveryRequestTransactionService preflight;
    private final CacheInvalidationExecutionPort execution;

    public CacheInvalidationRecoveryCoordinator(
            RecoveryRequestTransactionService preflight,
            CacheInvalidationExecutionPort execution) {
        this.preflight = Objects.requireNonNull(preflight, "preflight");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    @Override
    public CacheRecoveryCommandResult requestInitialExecution(CacheInvalidationRecoveryCommand command) {
        CacheRecoveryCommandResult result = preflight.preflight(command, CacheRecoveryAction.EXECUTE_INITIAL);
        if (result == CacheRecoveryCommandResult.ACCEPTED) execution.executeInitial(command.batchId());
        return result;
    }

    @Override
    public CacheRecoveryCommandResult requestRetry(CacheInvalidationRecoveryCommand command) {
        CacheRecoveryCommandResult result = preflight.preflight(command, CacheRecoveryAction.RETRY_FAILED);
        if (result == CacheRecoveryCommandResult.ACCEPTED) execution.executeManualRetry(command.batchId());
        return result;
    }

    @Override
    public CacheRecoveryCommandResult requestStrandedResume(CacheInvalidationRecoveryCommand command) {
        CacheRecoveryCommandResult result = preflight.preflight(command, CacheRecoveryAction.RESUME_STRANDED);
        if (result == CacheRecoveryCommandResult.ACCEPTED) execution.resumeStranded(command.batchId());
        return result;
    }
}

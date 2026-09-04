package dev.persefonia.app.platformoperations.operations;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.app.audit.integration.AdminAuditCommandFactory;
import dev.persefonia.app.audit.integration.AuditActionCatalog;
import dev.persefonia.app.audit.integration.AuditEntityCatalog;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryRequestTransactionService {
    private final CacheOperationsCommandAuthorizationPolicy authorization;
    private final CacheInvalidationBatchRepository batches;
    private final CacheInvalidationRecoveryPolicy recoveryPolicy;
    private final AppendAuditRecordPort audit;
    private final AdminAuditCommandFactory auditCommands;
    private final Clock clock;

    public RecoveryRequestTransactionService(
            CacheOperationsCommandAuthorizationPolicy authorization,
            CacheInvalidationBatchRepository batches,
            CacheInvalidationRecoveryPolicy recoveryPolicy,
            AppendAuditRecordPort audit,
            AdminAuditCommandFactory auditCommands,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditCommands = Objects.requireNonNull(auditCommands, "auditCommands");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CacheRecoveryCommandResult preflight(
            CacheInvalidationRecoveryCommand command,
            CacheRecoveryAction requiredAction) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(requiredAction, "requiredAction");
        authorization.requireOwner(command.actor(), commandName(requiredAction));
        CacheInvalidationBatch batch = batches.findById(command.batchId()).orElse(null);
        if (batch == null) return CacheRecoveryCommandResult.NOT_FOUND;
        if (recoveryPolicy.availableAction(batch, clock.instant()) != requiredAction) {
            return CacheRecoveryCommandResult.NOT_ELIGIBLE;
        }
        int attemptNumber = batch.attempts().size() + 1;
        audit.append(auditCommands.admin(
                auditAction(requiredAction), command.actor().identityRef(),
                AuditEntityCatalog.CACHE_INVALIDATION_BATCH, batch.id().value(), List.of(),
                List.of(metadata("attempt_number", attemptNumber))));
        return CacheRecoveryCommandResult.ACCEPTED;
    }

    private static String auditAction(CacheRecoveryAction action) {
        return switch (action) {
            case EXECUTE_INITIAL -> AuditActionCatalog.CACHE_INVALIDATION_INITIAL_EXECUTION_REQUESTED;
            case RETRY_FAILED -> AuditActionCatalog.CACHE_INVALIDATION_RETRY_REQUESTED;
            case RESUME_STRANDED -> AuditActionCatalog.CACHE_INVALIDATION_STRANDED_RESUME_REQUESTED;
            case NONE -> throw new IllegalArgumentException("NONE is not a recovery command");
        };
    }

    private static String commandName(CacheRecoveryAction action) {
        return switch (action) {
            case EXECUTE_INITIAL -> "execute initial cache purge";
            case RETRY_FAILED -> "retry failed cache targets";
            case RESUME_STRANDED -> "resume stranded cache purge";
            case NONE -> throw new IllegalArgumentException("NONE is not a recovery command");
        };
    }
}

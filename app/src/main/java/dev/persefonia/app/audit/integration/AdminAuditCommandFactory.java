package dev.persefonia.app.audit.integration;

import dev.persefonia.app.observability.CurrentRequestIdProvider;
import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditActorType;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class AdminAuditCommandFactory {
    private static final String ADMIN_CONTEXT = "iam";
    private static final String ADMIN_TYPE = "admin_account";
    private static final String BOOTSTRAP_DISPLAY = "Identity bootstrap";

    private final Clock clock;
    private final CurrentRequestIdProvider requestIds;

    public AdminAuditCommandFactory(Clock clock, CurrentRequestIdProvider requestIds) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
    }

    public AppendAuditRecordCommand admin(
            String action,
            UUID actorId,
            AuditEntityCatalog.Entity entity,
            UUID entityId,
            List<AppendAuditChangeCommand> changes,
            List<AppendAuditMetadataCommand> metadata) {
        Objects.requireNonNull(actorId, "actorId");
        return command(
                action,
                AuditActorType.ADMIN,
                ADMIN_CONTEXT,
                ADMIN_TYPE,
                actorId,
                adminDisplay(actorId),
                entity,
                entityId,
                changes,
                metadata);
    }

    public AppendAuditRecordCommand bootstrap(
            UUID accountId, List<AppendAuditMetadataCommand> metadata) {
        return command(
                AuditActionCatalog.ADMIN_ACCOUNT_BOOTSTRAPPED,
                AuditActorType.SYSTEM,
                null,
                null,
                null,
                BOOTSTRAP_DISPLAY,
                AuditEntityCatalog.ADMIN_ACCOUNT,
                accountId,
                List.of(),
                metadata);
    }

    private AppendAuditRecordCommand command(
            String action,
            AuditActorType actorType,
            String actorContext,
            String actorSourceType,
            UUID actorId,
            String actorDisplay,
            AuditEntityCatalog.Entity entity,
            UUID entityId,
            List<AppendAuditChangeCommand> changes,
            List<AppendAuditMetadataCommand> metadata) {
        Objects.requireNonNull(entity, "entity");
        return new AppendAuditRecordCommand(
                action,
                actorType,
                actorContext,
                actorSourceType,
                actorId,
                actorDisplay,
                entity.context(),
                entity.type(),
                Objects.requireNonNull(entityId, "entityId"),
                requestIds.currentRequestId().orElse(null),
                clock.instant(),
                changes,
                metadata);
    }

    static String adminDisplay(UUID actorId) {
        return "Admin " + actorId.toString().substring(0, 8);
    }

    public static AppendAuditMetadataCommand metadata(String key, Object value) {
        return new AppendAuditMetadataCommand(key, String.valueOf(value));
    }

    public static AppendAuditChangeCommand change(String field, Object oldValue, Object newValue) {
        return new AppendAuditChangeCommand(
                field,
                oldValue == null ? null : String.valueOf(oldValue),
                newValue == null ? null : String.valueOf(newValue));
    }
}

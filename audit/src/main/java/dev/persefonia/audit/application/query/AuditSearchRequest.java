package dev.persefonia.audit.application.query;

import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.time.Instant;
import java.util.Optional;

/** Framework-free, typed Audit search criteria. */
public record AuditSearchRequest(
        AuditAction action,
        AuditActorType actorType,
        SourceEntityId actorId,
        SourceContext entityContext,
        SourceType entityType,
        SourceEntityId entityId,
        Instant occurredFromInclusive,
        Instant occurredToExclusive,
        int page,
        int pageSize) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    public AuditSearchRequest {
        if (actorType == AuditActorType.SYSTEM && actorId != null) {
            throw new AuditValidationException("system actor filter must not include an actor id");
        }
        if (entityContext == null && (entityType != null || entityId != null)) {
            throw new AuditValidationException("entity type and id filters require an entity context");
        }
        if (entityId != null && entityType == null) {
            throw new AuditValidationException("entity id filter requires an entity type");
        }
        if (occurredFromInclusive != null
                && occurredToExclusive != null
                && !occurredFromInclusive.isBefore(occurredToExclusive)) {
            throw new AuditValidationException("audit occurrence range start must be before its end");
        }
        if (page < 1) {
            throw new AuditValidationException("audit search page must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new AuditValidationException("audit search page size must be between 1 and 100");
        }
    }

    public static AuditSearchRequest firstPage() {
        return new AuditSearchRequest(
                null, null, null, null, null, null, null, null, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    public Optional<AuditAction> actionOptional() { return Optional.ofNullable(action); }
    public Optional<AuditActorType> actorTypeOptional() { return Optional.ofNullable(actorType); }
    public Optional<SourceEntityId> actorIdOptional() { return Optional.ofNullable(actorId); }
    public Optional<SourceContext> entityContextOptional() { return Optional.ofNullable(entityContext); }
    public Optional<SourceType> entityTypeOptional() { return Optional.ofNullable(entityType); }
    public Optional<SourceEntityId> entityIdOptional() { return Optional.ofNullable(entityId); }
    public Optional<Instant> occurredFromInclusiveOptional() { return Optional.ofNullable(occurredFromInclusive); }
    public Optional<Instant> occurredToExclusiveOptional() { return Optional.ofNullable(occurredToExclusive); }
}

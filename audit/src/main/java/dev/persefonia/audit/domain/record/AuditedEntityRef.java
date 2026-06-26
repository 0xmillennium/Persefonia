package dev.persefonia.audit.domain.record;

/**
 * A typed reference to the source aggregate an audit record describes. It carries
 * a logical context, type, and id only; it never establishes a physical
 * cross-context foreign key.
 */
public record AuditedEntityRef(SourceContext context, SourceType type, SourceEntityId id) {
    public AuditedEntityRef {
        if (context == null) {
            throw new AuditValidationException("audited entity requires a context");
        }
        if (type == null) {
            throw new AuditValidationException("audited entity requires a type");
        }
        if (id == null) {
            throw new AuditValidationException("audited entity requires an id");
        }
    }

    public static AuditedEntityRef of(SourceContext context, SourceType type, SourceEntityId id) {
        return new AuditedEntityRef(context, type, id);
    }

    public static AuditedEntityRef of(String context, String type, java.util.UUID id) {
        if (id == null) {
            throw new AuditValidationException("audited entity requires an id");
        }
        return new AuditedEntityRef(SourceContext.of(context), SourceType.of(type), SourceEntityId.from(id));
    }
}

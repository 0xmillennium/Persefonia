package dev.persefonia.audit.domain.record;

import java.util.Objects;
import java.util.Optional;

/**
 * The actor responsible for an audited action. An {@code ADMIN} actor carries a
 * typed source reference (context, source type, id) and a display name. A
 * {@code SYSTEM} actor carries only a display name and never a source reference.
 */
public final class AuditActorRef {
    private final AuditActorType type;
    private final SourceContext context;
    private final SourceType sourceType;
    private final SourceEntityId id;
    private final DisplayName display;

    private AuditActorRef(
            AuditActorType type,
            SourceContext context,
            SourceType sourceType,
            SourceEntityId id,
            DisplayName display) {
        this.type = Objects.requireNonNull(type, "type");
        this.context = context;
        this.sourceType = sourceType;
        this.id = id;
        this.display = Objects.requireNonNull(display, "display");
    }

    public static AuditActorRef admin(
            SourceContext context,
            SourceType sourceType,
            SourceEntityId id,
            DisplayName display) {
        if (context == null) {
            throw new AuditValidationException("admin actor requires a source context");
        }
        if (sourceType == null) {
            throw new AuditValidationException("admin actor requires a source type");
        }
        if (id == null) {
            throw new AuditValidationException("admin actor requires an actor id");
        }
        if (display == null) {
            throw new AuditValidationException("admin actor requires a display name");
        }
        return new AuditActorRef(AuditActorType.ADMIN, context, sourceType, id, display);
    }

    public static AuditActorRef system(DisplayName display) {
        if (display == null) {
            throw new AuditValidationException("system actor requires a display name");
        }
        return new AuditActorRef(AuditActorType.SYSTEM, null, null, null, display);
    }

    public AuditActorType type() {
        return type;
    }

    public Optional<SourceContext> context() {
        return Optional.ofNullable(context);
    }

    public Optional<SourceType> sourceType() {
        return Optional.ofNullable(sourceType);
    }

    public Optional<SourceEntityId> id() {
        return Optional.ofNullable(id);
    }

    public DisplayName display() {
        return display;
    }
}

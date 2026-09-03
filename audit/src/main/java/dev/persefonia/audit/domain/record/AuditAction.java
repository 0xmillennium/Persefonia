package dev.persefonia.audit.domain.record;

public record AuditAction(String value) {
    public AuditAction {
        value = AuditIdentifierPolicy.action(value);
    }

    public static AuditAction of(String value) {
        return new AuditAction(value);
    }
}

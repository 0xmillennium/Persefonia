package dev.persefonia.audit.domain.record;

public record AuditAction(String value) {
    public AuditAction {
        value = AuditSafeText.actionToken(value, "audit action");
    }

    public static AuditAction of(String value) {
        return new AuditAction(value);
    }
}

package dev.persefonia.audit.domain.record;

public record SafeAuditValue(String value) {
    public SafeAuditValue {
        value = AuditValuePolicy.auditValue(value);
    }

    public static SafeAuditValue of(String value) {
        return new SafeAuditValue(value);
    }
}

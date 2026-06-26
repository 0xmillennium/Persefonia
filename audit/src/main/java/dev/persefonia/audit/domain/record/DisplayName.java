package dev.persefonia.audit.domain.record;

public record DisplayName(String value) {
    public DisplayName {
        value = AuditSafeText.displayName(value, "display name");
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }
}

package dev.persefonia.audit.domain.record;

public record DisplayName(String value) {
    public DisplayName {
        value = AuditTextRules.requiredSingleLine(value, "display name", AuditTextRules.MAX_DISPLAY_LENGTH);
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }
}

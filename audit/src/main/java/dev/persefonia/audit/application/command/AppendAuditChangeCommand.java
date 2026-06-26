package dev.persefonia.audit.application.command;

public record AppendAuditChangeCommand(String fieldPath, String oldValue, String newValue) {
}

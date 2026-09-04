package dev.persefonia.platformoperations.application.operations;

import java.util.Optional;

public record MigrationStatusSummary(
        String currentAppliedVersion,
        String latestResolvedVersion,
        int pendingCount,
        MigrationStatus status) {
    public MigrationStatusSummary {
        if (pendingCount < 0) throw new IllegalArgumentException("pending migration count cannot be negative");
        if (status == null) throw new NullPointerException("status");
    }
    public Optional<String> currentAppliedVersionOptional() { return Optional.ofNullable(currentAppliedVersion); }
    public Optional<String> latestResolvedVersionOptional() { return Optional.ofNullable(latestResolvedVersion); }
}

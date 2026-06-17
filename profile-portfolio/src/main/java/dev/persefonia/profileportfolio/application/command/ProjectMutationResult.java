package dev.persefonia.profileportfolio.application.command;

import java.time.Instant;
import java.util.UUID;

public record ProjectMutationResult(UUID projectId, boolean created, Instant updatedAt, long version) {
}

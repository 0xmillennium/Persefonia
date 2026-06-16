package dev.persefonia.profileportfolio.application.command;

import java.time.Instant;
import java.util.UUID;

public record PersonalProfileUpdateResult(UUID profileId, boolean created, Instant updatedAt, long version) {
}

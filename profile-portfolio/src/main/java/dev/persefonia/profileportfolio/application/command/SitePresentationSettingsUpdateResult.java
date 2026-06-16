package dev.persefonia.profileportfolio.application.command;

import java.time.Instant;
import java.util.UUID;

public record SitePresentationSettingsUpdateResult(UUID id, Instant updatedAt, long version) {
}

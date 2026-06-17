package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.UUID;

public record AdminProjectListItem(
        UUID id,
        String title,
        String status,
        String visibility,
        boolean featured,
        Integer sortOrder,
        Instant updatedAt) {
}

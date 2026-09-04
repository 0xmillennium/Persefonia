package dev.persefonia.profileportfolio.application.command;

import java.time.Instant;
import java.util.UUID;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicMutationFacts;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicExposureSnapshot;
import java.util.Map;

public record ProjectMutationResult(
        UUID projectId, boolean created, Instant updatedAt, long version,
        ProjectPublicMutationFacts publicMutationFacts) {
    public ProjectMutationResult(UUID projectId, boolean created, Instant updatedAt, long version) {
        this(projectId, created, updatedAt, version,
                new ProjectPublicMutationFacts(projectId,
                        new ProjectPublicExposureSnapshot(false, false, false, false),
                        new ProjectPublicExposureSnapshot(false, false, false, false), Map.of(), Map.of()));
    }
}

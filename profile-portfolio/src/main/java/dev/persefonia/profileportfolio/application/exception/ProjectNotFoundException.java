package dev.persefonia.profileportfolio.application.exception;

import dev.persefonia.profileportfolio.domain.project.ProjectId;
import java.util.UUID;

public final class ProjectNotFoundException extends ProjectApplicationException {
    public ProjectNotFoundException(ProjectId id) {
        super("Project was not found: " + id.value());
    }

    public ProjectNotFoundException(UUID id) {
        super("Project was not found: " + id);
    }
}

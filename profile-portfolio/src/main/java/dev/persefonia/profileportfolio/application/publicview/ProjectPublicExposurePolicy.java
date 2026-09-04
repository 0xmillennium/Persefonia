package dev.persefonia.profileportfolio.application.publicview;

import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import java.util.Objects;

public final class ProjectPublicExposurePolicy {
    public ProjectPublicExposureSnapshot snapshot(Project project) {
        Objects.requireNonNull(project, "project");
        return snapshot(project.status(), project.visibility(), project.featured());
    }

    public ProjectPublicExposureSnapshot snapshot(
            ProjectStatus status, ProjectVisibility visibility, boolean featured) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
        boolean direct = visibility == ProjectVisibility.PUBLIC || visibility == ProjectVisibility.UNLISTED;
        boolean listed = visibility == ProjectVisibility.PUBLIC && status != ProjectStatus.ARCHIVED;
        return new ProjectPublicExposureSnapshot(direct, listed, listed, listed && featured);
    }
}

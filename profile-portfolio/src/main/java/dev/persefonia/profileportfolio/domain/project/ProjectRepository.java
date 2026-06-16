package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import java.util.Optional;

public interface ProjectRepository {
    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    Optional<Project> findBySlug(ProjectSlug slug, ContentLanguage language);

    boolean existsSlug(ProjectSlug slug, ContentLanguage language);
}

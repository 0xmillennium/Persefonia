package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.application.publicview.ProjectPublicSurface;
import dev.persefonia.profileportfolio.domain.common.TagId;
import java.util.List;

public interface ProjectPublicSurfaceDependencyQuery {
    List<ProjectPublicSurface> findReferencing(TagId tagId, int limit);
}

package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.application.query.AdminProjectLinkView;
import dev.persefonia.profileportfolio.application.query.AdminProjectListItem;
import dev.persefonia.profileportfolio.application.query.AdminProjectLocalizationView;
import dev.persefonia.profileportfolio.application.query.AdminProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProjectAdminReadModel {
    List<AdminProjectListItem> list(ContentLanguage defaultLanguage);

    Optional<ProjectAdminDetails> findDetails(ProjectId projectId);

    record ProjectAdminDetails(
            UUID id,
            String status,
            String visibility,
            boolean featured,
            Integer sortOrder,
            Set<TagId> tagIds,
            List<AdminProjectLocalizationView> localizations,
            List<AdminProjectTechnologyView> technologies,
            List<AdminProjectLinkView> links,
            Instant updatedAt,
            long version) {
        public ProjectAdminDetails {
            tagIds = Set.copyOf(tagIds);
            localizations = List.copyOf(localizations);
            technologies = List.copyOf(technologies);
            links = List.copyOf(links);
        }
    }
}

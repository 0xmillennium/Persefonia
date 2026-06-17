package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.application.query.PublicProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.PublicProjectLinkView;
import dev.persefonia.profileportfolio.application.query.PublicProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProjectPublicReadModel {
    List<ProjectSummaryRow> listListedProjects(ContentLanguage language);

    Optional<ProjectDetailRow> findDetail(ProjectId projectId, ContentLanguage language, ProjectSlug expectedSlug);

    List<ProjectSummaryRow> listFeaturedProjects(ContentLanguage language, int limit);

    record ProjectSummaryRow(
            String title,
            String summary,
            String slug,
            Set<TagId> tagIds,
            List<PublicProjectTechnologyView> technologies) {
        public ProjectSummaryRow {
            tagIds = Set.copyOf(tagIds);
            technologies = List.copyOf(technologies);
        }
    }

    record ProjectDetailRow(
            String title,
            String summary,
            String slug,
            Set<TagId> tagIds,
            List<PublicProjectTechnologyView> technologies,
            List<PublicProjectLinkView> links,
            List<PublicProjectCaseStudySectionView> sections) {
        public ProjectDetailRow {
            tagIds = Set.copyOf(tagIds);
            technologies = List.copyOf(technologies);
            links = List.copyOf(links);
            sections = List.copyOf(sections);
        }
    }
}

package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.CaseStudySectionType;
import dev.persefonia.profileportfolio.domain.project.CaseStudyText;
import dev.persefonia.profileportfolio.domain.project.NormalizedTechnologyName;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectCaseStudySection;
import dev.persefonia.profileportfolio.domain.project.ProjectCaseStudySectionId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectLink;
import dev.persefonia.profileportfolio.domain.project.ProjectLinkId;
import dev.persefonia.profileportfolio.domain.project.ProjectLinkType;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalization;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalizationId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectSummary;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnology;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnologyId;
import dev.persefonia.profileportfolio.domain.project.ProjectTitle;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import dev.persefonia.profileportfolio.domain.project.TechnologyCategory;
import dev.persefonia.profileportfolio.domain.project.TechnologyName;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcProjectAdminReadModelAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Autowired ProjectAdminReadModel readModel;

    @Test
    void listsProjectsUsingConfiguredDefaultLanguageTitleWithFallback() {
        Project defaultLanguageProject = projects.save(project(
                "tr-project",
                List.of(localization(ContentLanguage.TR, "tr-project", "TR Project"),
                        localization(ContentLanguage.EN, "en-project", "EN Project"))));
        Project fallbackProject = projects.save(project(
                "only-en-project",
                List.of(localization(ContentLanguage.EN, "only-en-project", "Only EN"))));

        var rows = readModel.list(ContentLanguage.TR);

        assertThat(rows).extracting(row -> row.id()).contains(defaultLanguageProject.id().value(), fallbackProject.id().value());
        assertThat(rows).filteredOn(row -> row.id().equals(defaultLanguageProject.id().value()))
                .singleElement()
                .satisfies(row -> assertThat(row.title()).isEqualTo("TR Project"));
        assertThat(rows).filteredOn(row -> row.id().equals(fallbackProject.id().value()))
                .singleElement()
                .satisfies(row -> assertThat(row.title()).isEqualTo("Only EN"));
    }

    @Test
    void loadsProjectEditDetailsWithChildrenAndTagReferences() {
        TagId tagId = TagId.newId();
        Project saved = projects.save(project("sample-project", List.of(localization(ContentLanguage.TR, "sample-project", "Sample")), Set.of(tagId)));

        var details = readModel.findDetails(saved.id()).orElseThrow();

        assertThat(details.id()).isEqualTo(saved.id().value());
        assertThat(details.tagIds()).containsExactly(tagId);
        assertThat(details.localizations()).singleElement().satisfies(localization -> {
            assertThat(localization.language()).isEqualTo("TR");
            assertThat(localization.sections()).singleElement()
                    .satisfies(section -> assertThat(section.type()).isEqualTo("PROBLEM"));
        });
        assertThat(details.technologies()).singleElement()
                .satisfies(technology -> assertThat(technology.category()).isEqualTo("LANGUAGE"));
        assertThat(details.links()).singleElement()
                .satisfies(link -> assertThat(link.linkType()).isEqualTo("SOURCE"));
    }

    private static Project project(String slug, List<ProjectLocalization> localizations) {
        return project(slug, localizations, Set.of());
    }

    private static Project project(String slug, List<ProjectLocalization> localizations, Set<TagId> tagIds) {
        return Project.create(
                ProjectId.newId(),
                ProjectStatus.EXPERIMENT,
                ProjectVisibility.PRIVATE,
                false,
                SortOrder.of(1),
                null,
                tagIds,
                List.of(new ProjectTechnology(
                        ProjectTechnologyId.newId(),
                        TechnologyName.of("Java"),
                        NormalizedTechnologyName.of("java"),
                        TechnologyCategory.LANGUAGE,
                        SortOrder.of(1))),
                List.of(new ProjectLink(
                        ProjectLinkId.newId(),
                        LinkLabel.of("Source"),
                        ExternalUrl.of("https://example.test/" + slug),
                        ProjectLinkType.SOURCE,
                        SortOrder.of(1))),
                localizations,
                ContentLanguage.TR,
                NOW);
    }

    private static ProjectLocalization localization(ContentLanguage language, String slug, String title) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                language,
                ProjectSlug.of(slug),
                ProjectTitle.of(title),
                ProjectSummary.of("Summary"),
                List.of(new ProjectCaseStudySection(
                        ProjectCaseStudySectionId.newId(),
                        CaseStudySectionType.PROBLEM,
                        CaseStudyText.of("Body"),
                        SortOrder.of(1))));
    }
}

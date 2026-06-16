package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcProjectRepositoryAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void savesAndReloadsProjectWithOwnedChildGraphAndReferences() {
        TagId tagId = TagId.newId();
        AssetId assetId = AssetId.newId();
        Project saved = projects.save(project("sample-project", Set.of(tagId), assetId));

        Project reloaded = projects.findById(saved.id()).orElseThrow();

        assertThat(reloaded.localizations()).hasSize(1);
        assertThat(reloaded.localizations().getFirst().sections()).hasSize(1);
        assertThat(reloaded.technologies()).hasSize(1);
        assertThat(reloaded.links()).hasSize(1);
        assertThat(reloaded.tagIds()).containsExactly(tagId);
        assertThat(reloaded.coverAssetId()).contains(assetId);
        assertThat(projects.findBySlug(ProjectSlug.of("sample-project"), ContentLanguage.TR)).map(Project::id).contains(saved.id());
        assertThat(projects.existsSlug(ProjectSlug.of("sample-project"), ContentLanguage.TR)).isTrue();
        assertThat(projects.existsSlug(ProjectSlug.of("missing-project"), ContentLanguage.TR)).isFalse();
    }

    @Test
    void updatesProjectChildGraphWithoutLeavingOrphansAndUsesDomainVersion() {
        Project saved = projects.save(project("replace-me", Set.of(TagId.newId()), null));
        saved.replaceLinks(List.of(link(2)), ContentLanguage.TR, NOW.plusSeconds(1));
        saved.replaceTags(Set.of(TagId.newId()), ContentLanguage.TR, NOW.plusSeconds(2));

        Project updated = projects.save(saved);

        assertThat(updated.version().value()).isEqualTo(2);
        assertThat(updated.links()).extracting(link -> link.sortOrder().value()).containsExactly(2);
        assertThat(updated.tagIds()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.project_links", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.project_tags", Long.class)).isEqualTo(1);
    }

    @Test
    void reloadsFeaturedEnglishOnlyProjectWithoutAssumingTurkishDefaultLanguage() {
        ProjectId projectId = ProjectId.newId();
        insertFeaturedEnglishOnlyProject(projectId);

        Project reloaded = projects.findById(projectId).orElseThrow();

        assertThat(reloaded.featured()).isTrue();
        assertThat(reloaded.localizations()).extracting(ProjectLocalization::language).containsExactly(ContentLanguage.EN);
        assertThat(projects.findBySlug(ProjectSlug.of("english-featured"), ContentLanguage.EN))
                .map(Project::id)
                .contains(projectId);
    }

    private static Project project(String slug, Set<TagId> tagIds, AssetId assetId) {
        return Project.create(
                ProjectId.newId(),
                ProjectStatus.ACTIVE,
                ProjectVisibility.PUBLIC,
                false,
                SortOrder.of(1),
                assetId,
                tagIds,
                List.of(technology(1)),
                List.of(link(1)),
                List.of(localization(slug)),
                ContentLanguage.TR,
                NOW);
    }

    private static ProjectLocalization localization(String slug) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                ContentLanguage.TR,
                ProjectSlug.of(slug),
                ProjectTitle.of("Sample Project"),
                ProjectSummary.of("Summary"),
                List.of(new ProjectCaseStudySection(
                        ProjectCaseStudySectionId.newId(),
                        CaseStudySectionType.PROBLEM,
                        CaseStudyText.of("Body"),
                        SortOrder.of(1))));
    }

    private static ProjectTechnology technology(int sortOrder) {
        return new ProjectTechnology(
                ProjectTechnologyId.newId(),
                TechnologyName.of("Java"),
                NormalizedTechnologyName.of("java"),
                TechnologyCategory.LANGUAGE,
                SortOrder.of(sortOrder));
    }

    private static ProjectLink link(int sortOrder) {
        return new ProjectLink(
                ProjectLinkId.newId(),
                LinkLabel.of("Demo"),
                ExternalUrl.of("https://example.test/" + sortOrder),
                ProjectLinkType.DEMO,
                SortOrder.of(sortOrder));
    }

    private void insertFeaturedEnglishOnlyProject(ProjectId projectId) {
        UUID localizationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO portfolio.projects (
                    id, status, visibility, featured, created_at, updated_at, version
                ) VALUES (?, 'ACTIVE', 'PUBLIC', true, ?, ?, 0)
                """, projectId.value(), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO portfolio.project_localizations (
                    id, project_id, language, slug, title, summary
                ) VALUES (?, ?, 'EN', 'english-featured', 'English Featured', 'Summary')
                """, localizationId, projectId.value());
    }
}

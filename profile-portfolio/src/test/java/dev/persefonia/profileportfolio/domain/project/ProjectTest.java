package dev.persefonia.profileportfolio.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void keepsProjectStatusAndProjectVisibilitySeparate() {
        assertThat(ProjectStatus.ACTIVE.name()).isNotEqualTo(ProjectVisibility.PUBLIC.name());
    }

    @Test
    void rejectsPublicProjectWithoutLocalization() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsFeaturedPrivateProject() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.PRIVATE, true, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsFeaturedUnlistedProject() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.UNLISTED, true, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsFeaturedArchivedProject() {
        assertThatThrownBy(() -> project(ProjectStatus.ARCHIVED, ProjectVisibility.PUBLIC, true, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsFeaturedProjectWithoutDefaultLanguageLocalization() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, true, List.of(localization(ContentLanguage.EN)), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rehydrateDoesNotRequireDefaultLanguageLocalization() {
        Project project = Project.rehydrate(
                ProjectId.newId(),
                ProjectStatus.ACTIVE,
                ProjectVisibility.PUBLIC,
                true,
                null,
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of(localization(ContentLanguage.EN)),
                NOW,
                NOW,
                dev.persefonia.profileportfolio.domain.common.Version.of(0));

        assertThat(project.localizations()).extracting(ProjectLocalization::language).containsExactly(ContentLanguage.EN);
    }

    @Test
    void validateFeaturedEligibilityAcceptsConfiguredDefaultLanguageLocalization() {
        Project project = project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, true, List.of(localization(ContentLanguage.EN)), List.of(), List.of(), Set.of(), null, ContentLanguage.EN);

        project.validateFeaturedEligibility(ContentLanguage.EN);
    }

    @Test
    void validateFeaturedEligibilityRejectsMissingConfiguredDefaultLanguageLocalization() {
        Project project = Project.rehydrate(
                ProjectId.newId(),
                ProjectStatus.ACTIVE,
                ProjectVisibility.PUBLIC,
                true,
                null,
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of(localization(ContentLanguage.EN)),
                NOW,
                NOW,
                dev.persefonia.profileportfolio.domain.common.Version.of(0));

        assertThatThrownBy(() -> project.validateFeaturedEligibility(ContentLanguage.TR))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rehydrateStillRejectsFeaturedPrivateProject() {
        assertThatThrownBy(() -> Project.rehydrate(
                ProjectId.newId(),
                ProjectStatus.ACTIVE,
                ProjectVisibility.PRIVATE,
                true,
                null,
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of(localization(ContentLanguage.EN)),
                NOW,
                NOW,
                dev.persefonia.profileportfolio.domain.common.Version.of(0)))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rehydrateStillRejectsFeaturedArchivedProject() {
        assertThatThrownBy(() -> Project.rehydrate(
                ProjectId.newId(),
                ProjectStatus.ARCHIVED,
                ProjectVisibility.PUBLIC,
                true,
                null,
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of(localization(ContentLanguage.EN)),
                NOW,
                NOW,
                dev.persefonia.profileportfolio.domain.common.Version.of(0)))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void setFeaturedStillRejectsStructurallyIneligibleProject() {
        Project project = project(ProjectStatus.ACTIVE, ProjectVisibility.PRIVATE, false, List.of(), List.of(), List.of(), Set.of(), null);

        assertThatThrownBy(() -> project.setFeatured(true, ContentLanguage.TR, NOW.plusSeconds(1)))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsDuplicateLocalizationLanguage() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(localization(ContentLanguage.TR), localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsDuplicateProjectLinkSortOrder() {
        assertThatThrownBy(() -> project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(localization(ContentLanguage.TR)), List.of(), List.of(link(1), link(1)), Set.of(), null))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void allowsTagIdReferenceWithoutLoadingTagEntity() {
        TagId tagId = TagId.newId();
        Project project = project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(tagId), null);

        assertThat(project.tagIds()).containsExactly(tagId);
    }

    @Test
    void allowsAssetIdReferenceWithoutLoadingAssetEntity() {
        AssetId assetId = AssetId.newId();
        Project project = project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), assetId);

        assertThat(project.coverAssetId()).contains(assetId);
    }

    @Test
    void atomicUpdateDetailsIncrementsVersionOnce() {
        Project project = project(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, false, List.of(localization(ContentLanguage.TR)), List.of(), List.of(), Set.of(), null);

        project.updateDetails(
                ProjectStatus.COMPLETED,
                ProjectVisibility.PRIVATE,
                false,
                SortOrder.of(2),
                Set.of(TagId.newId()),
                List.of(technology(1)),
                List.of(link(1)),
                List.of(localization(ContentLanguage.EN)),
                ContentLanguage.TR,
                NOW.plusSeconds(1));

        assertThat(project.version().value()).isEqualTo(1);
        assertThat(project.status()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.visibility()).isEqualTo(ProjectVisibility.PRIVATE);
    }

    private static Project project(
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            List<ProjectLocalization> localizations,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            Set<TagId> tagIds,
            AssetId assetId) {
        return Project.create(
                ProjectId.newId(),
                status,
                visibility,
                featured,
                null,
                assetId,
                tagIds,
                technologies,
                links,
                localizations,
                ContentLanguage.TR,
                NOW);
    }

    private static Project project(
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            List<ProjectLocalization> localizations,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            Set<TagId> tagIds,
            AssetId assetId,
            ContentLanguage defaultLanguage) {
        return Project.create(
                ProjectId.newId(),
                status,
                visibility,
                featured,
                null,
                assetId,
                tagIds,
                technologies,
                links,
                localizations,
                defaultLanguage,
                NOW);
    }

    private static ProjectLocalization localization(ContentLanguage language) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                language,
                ProjectSlug.of(language.name().toLowerCase() + "-project"),
                ProjectTitle.of("Sample Project"),
                ProjectSummary.of("Summary"),
                List.of());
    }

    private static ProjectLink link(int sortOrder) {
        return new ProjectLink(
                ProjectLinkId.newId(),
                LinkLabel.of("Demo"),
                ExternalUrl.of("https://example.test"),
                ProjectLinkType.DEMO,
                SortOrder.of(sortOrder));
    }

    private static ProjectTechnology technology(int sortOrder) {
        return new ProjectTechnology(
                ProjectTechnologyId.newId(),
                TechnologyName.of("Java"),
                NormalizedTechnologyName.of("java"),
                TechnologyCategory.LANGUAGE,
                SortOrder.of(sortOrder));
    }
}

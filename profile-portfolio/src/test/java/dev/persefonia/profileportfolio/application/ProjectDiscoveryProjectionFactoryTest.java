package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.profileportfolio.application.discovery.ConfiguredProjectCanonicalUrlFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectDiscoveryProjectionFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalization;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalizationId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectSummary;
import dev.persefonia.profileportfolio.domain.project.ProjectTitle;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectDiscoveryProjectionFactoryTest {
    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");

    private final ProjectDiscoveryProjectionFactory factory = new ProjectDiscoveryProjectionFactory(
            new ProjectPublicRouteFactory(),
            new ConfiguredProjectCanonicalUrlFactory("https://example.test"));

    @Test
    void publicProjectCreatesConservativeProjectionPerLocalization() {
        Project project = project(ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, localization(ContentLanguage.TR), localization(ContentLanguage.EN));

        var projections = factory.projectionsFor(project);

        assertThat(projections).hasSize(2);
        assertThat(projections).allSatisfy(input -> {
            assertThat(input.sourceContext()).isEqualTo(SourceContext.PROFILE_PORTFOLIO);
            assertThat(input.sourceType()).isEqualTo(SourceType.PROJECT);
            assertThat(input.resourceType()).isEqualTo(DiscoverableResourceType.PROJECT);
            assertThat(input.routePurpose()).isEqualTo(RoutePurpose.DETAIL);
            assertThat(input.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
            assertThat(input.searchEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.sitemapEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.openGraphImageAssetId()).isNull();
            assertThat(input.publishedAt()).isNull();
        });
        assertThat(projections).extracting(input -> input.publicUrl().value())
                .containsExactly("/tr/projects/tr-project", "/en/projects/en-project");
    }

    @Test
    void unlistedProjectCreatesDirectDetailProjection() {
        Project project = project(ProjectVisibility.UNLISTED, ProjectStatus.ACTIVE, localization(ContentLanguage.EN));

        assertThat(factory.projectionsFor(project))
                .singleElement()
                .satisfies(input -> assertThat(input.publicUrl().value()).isEqualTo("/en/projects/en-project"));
    }

    @Test
    void privateAndArchivedProjectsProduceNoProjections() {
        assertThat(factory.projectionsFor(project(ProjectVisibility.PRIVATE, ProjectStatus.ACTIVE, localization(ContentLanguage.TR))))
                .isEmpty();
        assertThat(factory.projectionsFor(project(ProjectVisibility.PUBLIC, ProjectStatus.ARCHIVED, localization(ContentLanguage.TR))))
                .isEmpty();
    }

    private static Project project(
            ProjectVisibility visibility,
            ProjectStatus status,
            ProjectLocalization... localizations) {
        return Project.create(
                ProjectId.newId(),
                status,
                visibility,
                false,
                SortOrder.of(1),
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of(localizations),
                ContentLanguage.TR,
                NOW);
    }

    private static ProjectLocalization localization(ContentLanguage language) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                language,
                ProjectSlug.of(language.name().toLowerCase(java.util.Locale.ROOT) + "-project"),
                ProjectTitle.of("Project " + language.name()),
                ProjectSummary.of("Summary " + language.name()),
                List.of());
    }
}

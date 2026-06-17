package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagAssignmentValidation;
import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView;
import dev.persefonia.profileportfolio.application.query.PublicProjectCardView;
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
import dev.persefonia.profileportfolio.application.service.PublicFeaturedProjectQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProjectListingQueryService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcProjectPublicReadModelAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Autowired ProjectPublicReadModel publicReadModel;

    @Test
    void listingIncludesOnlyPublicNonArchivedLocalizedProjects() {
        Project listed = projects.save(project("listed", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        projects.save(project("unlisted", ProjectVisibility.UNLISTED, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        projects.save(project("private", ProjectVisibility.PRIVATE, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        projects.save(project("archived", ProjectVisibility.PUBLIC, ProjectStatus.ARCHIVED, false, ContentLanguage.TR));
        projects.save(project("english", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false, ContentLanguage.EN));

        var rows = publicReadModel.listListedProjects(ContentLanguage.TR);

        assertThat(rows).extracting(ProjectPublicReadModel.ProjectSummaryRow::slug).containsExactly("listed");
        assertThat(rows.getFirst().title()).isEqualTo("Project listed");
        assertThat(rows.getFirst().tagIds()).containsExactlyElementsOf(listed.tagIds());
    }

    @Test
    void listingMapsTechnologiesForMultipleProjects() {
        projects.save(project(
                "api",
                ProjectVisibility.PUBLIC,
                ProjectStatus.ACTIVE,
                false,
                ContentLanguage.TR,
                Set.of(TagId.newId()),
                List.of(technology("Java", TechnologyCategory.LANGUAGE, 1),
                        technology("PostgreSQL", TechnologyCategory.DATABASE, 2)),
                1));
        projects.save(project(
                "ops",
                ProjectVisibility.PUBLIC,
                ProjectStatus.ACTIVE,
                false,
                ContentLanguage.TR,
                Set.of(TagId.newId()),
                List.of(technology("Docker", TechnologyCategory.TOOL, 1),
                        technology("Terraform", TechnologyCategory.TOOL, 2)),
                2));

        var bySlug = summaryRowsBySlug(publicReadModel.listListedProjects(ContentLanguage.TR));

        assertThat(bySlug.get("api").technologies()).extracting(technology -> technology.name())
                .containsExactly("Java", "PostgreSQL");
        assertThat(bySlug.get("ops").technologies()).extracting(technology -> technology.name())
                .containsExactly("Docker", "Terraform");
    }

    @Test
    void listingMapsActiveTagsForMultipleProjectsAndHidesArchivedTags() {
        TagId platform = TagId.newId();
        TagId archived = TagId.newId();
        TagId research = TagId.newId();
        projects.save(project("api", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false,
                ContentLanguage.TR, Set.of(platform, archived), defaultTechnologies(), 1));
        projects.save(project("lab", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false,
                ContentLanguage.TR, Set.of(research), defaultTechnologies(), 2));

        PublicProjectListingQueryService service = new PublicProjectListingQueryService(
                publicReadModel,
                new Vocabulary(Map.of(
                        platform, new ProjectTagDetails(platform.value(), "Platform", "platform", false),
                        archived, new ProjectTagDetails(archived.value(), "Archived", "archived", true),
                        research, new ProjectTagDetails(research.value(), "Research", "research", false))),
                new ProjectPublicRouteFactory());

        Map<String, PublicProjectCardView> bySlug = listedCardsBySlug(service.list(ContentLanguage.TR));

        assertThat(bySlug.get("api").tags()).extracting(tag -> tag.name()).containsExactly("Platform");
        assertThat(bySlug.get("lab").tags()).extracting(tag -> tag.name()).containsExactly("Research");
    }

    @Test
    void detailIncludesPublicAndUnlistedButRejectsPrivateArchivedLanguageAndSlugMismatch() {
        Project publicProject = projects.save(project("public-detail", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        Project unlistedProject = projects.save(project("unlisted-detail", ProjectVisibility.UNLISTED, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        Project privateProject = projects.save(project("private-detail", ProjectVisibility.PRIVATE, ProjectStatus.ACTIVE, false, ContentLanguage.TR));
        Project archivedProject = projects.save(project("archived-detail", ProjectVisibility.PUBLIC, ProjectStatus.ARCHIVED, false, ContentLanguage.TR));

        assertThat(publicReadModel.findDetail(publicProject.id(), ContentLanguage.TR, ProjectSlug.of("public-detail"))).isPresent();
        assertThat(publicReadModel.findDetail(unlistedProject.id(), ContentLanguage.TR, ProjectSlug.of("unlisted-detail"))).isPresent();
        assertThat(publicReadModel.findDetail(privateProject.id(), ContentLanguage.TR, ProjectSlug.of("private-detail"))).isEmpty();
        assertThat(publicReadModel.findDetail(archivedProject.id(), ContentLanguage.TR, ProjectSlug.of("archived-detail"))).isEmpty();
        assertThat(publicReadModel.findDetail(publicProject.id(), ContentLanguage.EN, ProjectSlug.of("public-detail"))).isEmpty();
        assertThat(publicReadModel.findDetail(publicProject.id(), ContentLanguage.TR, ProjectSlug.of("old-detail"))).isEmpty();
    }

    @Test
    void featuredIncludesOnlyPublicFeaturedNonArchivedLocalizedProjectsAndRespectsLimit() {
        projects.save(project("first", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, true, ContentLanguage.EN));
        projects.save(project("second", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, true, ContentLanguage.EN));
        projects.save(project("unlisted", ProjectVisibility.UNLISTED, ProjectStatus.ACTIVE, false, ContentLanguage.EN));
        projects.save(project("private", ProjectVisibility.PRIVATE, ProjectStatus.ACTIVE, false, ContentLanguage.EN));
        projects.save(project("archived", ProjectVisibility.PUBLIC, ProjectStatus.ARCHIVED, false, ContentLanguage.EN));
        projects.save(project("turkish", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, true, ContentLanguage.TR));

        var rows = publicReadModel.listFeaturedProjects(ContentLanguage.EN, 1);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().slug()).isIn("first", "second");
    }

    @Test
    void featuredMapsTechnologiesForMultipleProjects() {
        projects.save(project(
                "featured-api",
                ProjectVisibility.PUBLIC,
                ProjectStatus.ACTIVE,
                true,
                ContentLanguage.EN,
                Set.of(TagId.newId()),
                List.of(technology("Java", TechnologyCategory.LANGUAGE, 1)),
                1));
        projects.save(project(
                "featured-ops",
                ProjectVisibility.PUBLIC,
                ProjectStatus.ACTIVE,
                true,
                ContentLanguage.EN,
                Set.of(TagId.newId()),
                List.of(technology("Docker", TechnologyCategory.TOOL, 1)),
                2));

        var bySlug = summaryRowsBySlug(publicReadModel.listFeaturedProjects(ContentLanguage.EN, 5));

        assertThat(bySlug.get("featured-api").technologies()).extracting(technology -> technology.name())
                .containsExactly("Java");
        assertThat(bySlug.get("featured-ops").technologies()).extracting(technology -> technology.name())
                .containsExactly("Docker");
    }

    @Test
    void featuredMapsActiveTagsForMultipleProjectsAndHidesArchivedTags() {
        TagId platform = TagId.newId();
        TagId archived = TagId.newId();
        TagId delivery = TagId.newId();
        projects.save(project("featured-api", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, true,
                ContentLanguage.EN, Set.of(platform, archived), defaultTechnologies(), 1));
        projects.save(project("featured-delivery", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, true,
                ContentLanguage.EN, Set.of(delivery), defaultTechnologies(), 2));

        PublicFeaturedProjectQueryService service = new PublicFeaturedProjectQueryService(
                publicReadModel,
                new Vocabulary(Map.of(
                        platform, new ProjectTagDetails(platform.value(), "Platform", "platform", false),
                        archived, new ProjectTagDetails(archived.value(), "Archived", "archived", true),
                        delivery, new ProjectTagDetails(delivery.value(), "Delivery", "delivery", false))),
                new ProjectPublicRouteFactory());

        Map<String, PublicFeaturedProjectView> bySlug = featuredCardsBySlug(service.list(ContentLanguage.EN, 5));

        assertThat(bySlug.get("featured-api").tags()).extracting(tag -> tag.name()).containsExactly("Platform");
        assertThat(bySlug.get("featured-delivery").tags()).extracting(tag -> tag.name()).containsExactly("Delivery");
    }

    @Test
    void detailChildrenAreSortedAndDoesNotExposeMediaFields() {
        Project project = projects.save(project("children", ProjectVisibility.PUBLIC, ProjectStatus.ACTIVE, false, ContentLanguage.TR));

        var row = publicReadModel.findDetail(project.id(), ContentLanguage.TR, ProjectSlug.of("children")).orElseThrow();

        assertThat(row.technologies()).extracting(technology -> technology.name()).containsExactly("Java", "PostgreSQL");
        assertThat(row.links()).extracting(link -> link.label()).containsExactly("Source", "Demo");
        assertThat(row.sections()).extracting(section -> section.type()).containsExactly("PROBLEM", "RESULT");
    }

    private static Project project(
            String slug,
            ProjectVisibility visibility,
            ProjectStatus status,
            boolean featured,
            ContentLanguage language) {
        return project(slug, visibility, status, featured, language, Set.of(TagId.newId()), defaultTechnologies(), 1);
    }

    private static Project project(
            String slug,
            ProjectVisibility visibility,
            ProjectStatus status,
            boolean featured,
            ContentLanguage language,
            Set<TagId> tagIds,
            List<ProjectTechnology> technologies,
            int sortOrder) {
        return Project.create(
                ProjectId.newId(),
                status,
                visibility,
                featured,
                SortOrder.of(sortOrder),
                null,
                tagIds,
                technologies,
                List.of(link("Demo", ProjectLinkType.DEMO, 2, slug),
                        link("Source", ProjectLinkType.SOURCE, 1, slug)),
                List.of(localization(language, slug)),
                language,
                NOW);
    }

    private static List<ProjectTechnology> defaultTechnologies() {
        return List.of(technology("PostgreSQL", TechnologyCategory.DATABASE, 2),
                technology("Java", TechnologyCategory.LANGUAGE, 1));
    }

    private static ProjectLocalization localization(ContentLanguage language, String slug) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                language,
                ProjectSlug.of(slug),
                ProjectTitle.of("Project " + slug),
                ProjectSummary.of("Summary " + slug),
                List.of(section(CaseStudySectionType.RESULT, 2), section(CaseStudySectionType.PROBLEM, 1)));
    }

    private static ProjectCaseStudySection section(CaseStudySectionType type, int sortOrder) {
        return new ProjectCaseStudySection(
                ProjectCaseStudySectionId.newId(),
                type,
                CaseStudyText.of(type.name() + " body"),
                SortOrder.of(sortOrder));
    }

    private static ProjectTechnology technology(String name, TechnologyCategory category, int sortOrder) {
        return new ProjectTechnology(
                ProjectTechnologyId.newId(),
                TechnologyName.of(name),
                NormalizedTechnologyName.of(name.toLowerCase(java.util.Locale.ROOT)),
                category,
                SortOrder.of(sortOrder));
    }

    private static ProjectLink link(String label, ProjectLinkType type, int sortOrder, String slug) {
        return new ProjectLink(
                ProjectLinkId.newId(),
                LinkLabel.of(label),
                ExternalUrl.of("https://example.test/" + slug + "/" + sortOrder),
                type,
                SortOrder.of(sortOrder));
    }

    private static Map<String, ProjectPublicReadModel.ProjectSummaryRow> summaryRowsBySlug(
            List<ProjectPublicReadModel.ProjectSummaryRow> rows) {
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                ProjectPublicReadModel.ProjectSummaryRow::slug,
                java.util.function.Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static Map<String, PublicProjectCardView> listedCardsBySlug(List<PublicProjectCardView> cards) {
        return cards.stream().collect(java.util.stream.Collectors.toMap(
                PublicProjectCardView::slug,
                java.util.function.Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static Map<String, PublicFeaturedProjectView> featuredCardsBySlug(List<PublicFeaturedProjectView> cards) {
        return cards.stream().collect(java.util.stream.Collectors.toMap(
                PublicFeaturedProjectView::slug,
                java.util.function.Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static final class Vocabulary implements ProjectTagVocabularyPort {
        private final Map<TagId, ProjectTagDetails> tags;

        private Vocabulary(Map<TagId, ProjectTagDetails> tags) {
            this.tags = Map.copyOf(tags);
        }

        @Override
        public List<ProjectTagOption> findAssignableTags() {
            return tags.values().stream()
                    .filter(tag -> !tag.archived())
                    .map(tag -> new ProjectTagOption(tag.id(), tag.name(), tag.slug()))
                    .toList();
        }

        @Override
        public List<ProjectTagDetails> findByIds(Set<TagId> ids) {
            return ids.stream().map(tags::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public ProjectTagAssignmentValidation validateAssignments(Set<TagId> currentlyAssigned, Set<TagId> requested) {
            Set<TagId> missing = requested.stream()
                    .filter(id -> !tags.containsKey(id))
                    .collect(java.util.stream.Collectors.toSet());
            Set<TagId> archived = requested.stream()
                    .filter(id -> tags.containsKey(id) && tags.get(id).archived() && !currentlyAssigned.contains(id))
                    .collect(java.util.stream.Collectors.toSet());
            return new ProjectTagAssignmentValidation(missing, archived);
        }
    }
}

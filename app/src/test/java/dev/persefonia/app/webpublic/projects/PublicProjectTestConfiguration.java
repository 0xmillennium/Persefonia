package dev.persefonia.app.webpublic.projects;

import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagAssignmentValidation;
import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.PublicProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.PublicProjectLinkView;
import dev.persefonia.profileportfolio.application.query.PublicProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-project-mvc-test")
public class PublicProjectTestConfiguration {
    @Bean
    @Primary
    InMemoryProjectPublicReadModel publicProjectReadModel() {
        return new InMemoryProjectPublicReadModel();
    }

    @Bean
    @Primary
    PublicProjectTagVocabulary publicProjectTagVocabulary() {
        return new PublicProjectTagVocabulary();
    }

    public static final class InMemoryProjectPublicReadModel implements ProjectPublicReadModel {
        private final Map<ProjectId, ProjectRecord> projects = new LinkedHashMap<>();

        public void reset() {
            projects.clear();
        }

        public ProjectId add(ProjectRecord project) {
            projects.put(project.id(), project);
            return project.id();
        }

        @Override
        public List<ProjectSummaryRow> listListedProjects(ContentLanguage language) {
            return projects.values().stream()
                    .filter(project -> project.visibility() == Visibility.PUBLIC)
                    .filter(project -> project.status() != Status.ARCHIVED)
                    .map(project -> project.summaryRow(language))
                    .flatMap(PublicProjectTestConfiguration::stream)
                    .toList();
        }

        @Override
        public Optional<ProjectDetailRow> findDetail(ProjectId projectId, ContentLanguage language, ProjectSlug expectedSlug) {
            return Optional.ofNullable(projects.get(projectId))
                    .filter(project -> project.visibility() == Visibility.PUBLIC || project.visibility() == Visibility.UNLISTED)
                    .flatMap(project -> project.detailRow(language, expectedSlug.value()));
        }

        @Override
        public List<ProjectSummaryRow> listFeaturedProjects(ContentLanguage language, int limit) {
            return projects.values().stream()
                    .filter(record -> record.featured())
                    .filter(project -> project.visibility() == Visibility.PUBLIC)
                    .filter(project -> project.status() != Status.ARCHIVED)
                    .map(project -> project.summaryRow(language))
                    .flatMap(PublicProjectTestConfiguration::stream)
                    .limit(limit)
                    .toList();
        }
    }

    public static final class PublicProjectTagVocabulary implements ProjectTagVocabularyPort {
        private final Map<TagId, ProjectTagDetails> tags = new LinkedHashMap<>();

        void reset() {
            tags.clear();
        }

        TagId active(String name, String slug) {
            TagId id = TagId.newId();
            tags.put(id, new ProjectTagDetails(id.value(), name, slug, false));
            return id;
        }

        TagId archived(String name, String slug) {
            TagId id = TagId.newId();
            tags.put(id, new ProjectTagDetails(id.value(), name, slug, true));
            return id;
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
            Set<TagId> missing = new LinkedHashSet<>(requested);
            missing.removeAll(tags.keySet());
            Set<TagId> archived = requested.stream()
                    .filter(id -> tags.containsKey(id) && tags.get(id).archived() && !currentlyAssigned.contains(id))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return new ProjectTagAssignmentValidation(missing, archived);
        }
    }

    public record ProjectRecord(
            ProjectId id,
            Visibility visibility,
            Status status,
            boolean featured,
            Set<TagId> tagIds,
            List<PublicProjectTechnologyView> technologies,
            List<PublicProjectLinkView> links,
            Map<ContentLanguage, Localization> localizations) {
        public ProjectRecord {
            tagIds = Set.copyOf(tagIds);
            technologies = List.copyOf(technologies);
            links = List.copyOf(links);
            localizations = Map.copyOf(localizations);
        }

        public static ProjectRecord project(String slug, Visibility visibility, Status status, ContentLanguage language) {
            return new ProjectRecord(
                    ProjectId.from(UUID.randomUUID()),
                    visibility,
                    status,
                    false,
                    Set.of(),
                    List.of(technology("Java", "LANGUAGE")),
                    List.of(link("Demo", "https://example.test/" + slug, "DEMO")),
                    Map.of(language, localization(slug, "Project " + slug, "Summary " + slug)));
        }

        public ProjectRecord featured(boolean featured) {
            return new ProjectRecord(id, visibility, status, featured, tagIds, technologies, links, localizations);
        }

        public ProjectRecord tags(Set<TagId> tagIds) {
            return new ProjectRecord(id, visibility, status, featured, tagIds, technologies, links, localizations);
        }

        public ProjectRecord withLocalization(ContentLanguage language, String slug, String title) {
            Map<ContentLanguage, Localization> copy = new LinkedHashMap<>(localizations);
            copy.put(language, localization(slug, title, "Summary " + title));
            return new ProjectRecord(id, visibility, status, featured, tagIds, technologies, links, copy);
        }

        Optional<ProjectPublicReadModel.ProjectSummaryRow> summaryRow(ContentLanguage language) {
            return Optional.ofNullable(localizations.get(language))
                    .map(localization -> new ProjectPublicReadModel.ProjectSummaryRow(
                            localization.title(),
                            localization.summary(),
                            localization.slug(),
                            tagIds,
                            technologies));
        }

        Optional<ProjectPublicReadModel.ProjectDetailRow> detailRow(ContentLanguage language, String expectedSlug) {
            return Optional.ofNullable(localizations.get(language))
                    .filter(localization -> localization.slug().equals(expectedSlug))
                    .map(localization -> new ProjectPublicReadModel.ProjectDetailRow(
                            localization.title(),
                            localization.summary(),
                            localization.slug(),
                            tagIds,
                            technologies,
                            links,
                            localization.sections()));
        }
    }

    public enum Visibility {
        PUBLIC,
        UNLISTED,
        PRIVATE
    }

    public enum Status {
        ACTIVE,
        ARCHIVED
    }

    public record Localization(
            String slug,
            String title,
            String summary,
            List<PublicProjectCaseStudySectionView> sections) {
        public Localization {
            sections = List.copyOf(sections);
        }
    }

    static Localization localization(String slug, String title, String summary) {
        return new Localization(slug, title, summary, List.of(new PublicProjectCaseStudySectionView("PROBLEM", "Problem body")));
    }

    static PublicProjectTechnologyView technology(String name, String category) {
        return new PublicProjectTechnologyView(name, category);
    }

    static PublicProjectLinkView link(String label, String url, String linkType) {
        return new PublicProjectLinkView(label, url, linkType);
    }

    private static <T> Stream<T> stream(Optional<T> optional) {
        return optional.map(Stream::of).orElseGet(Stream::empty);
    }
}

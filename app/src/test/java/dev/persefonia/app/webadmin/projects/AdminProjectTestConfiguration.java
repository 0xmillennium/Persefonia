package dev.persefonia.app.webadmin.projects;

import dev.persefonia.app.audit.MvcAuditTestConfiguration;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.profileportfolio.application.port.ProjectTagAssignmentValidation;
import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.application.query.AdminProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.AdminProjectLinkView;
import dev.persefonia.profileportfolio.application.query.AdminProjectListItem;
import dev.persefonia.profileportfolio.application.query.AdminProjectLocalizationView;
import dev.persefonia.profileportfolio.application.query.AdminProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-project-mvc-test")
@Import(MvcAuditTestConfiguration.class)
class AdminProjectTestConfiguration {
    @Bean
    @Primary
    AdminProjectTestRepository adminProjectTestRepository() {
        return new AdminProjectTestRepository();
    }

    @Bean
    @Primary
    AdminProjectSettingsRepository adminProjectSettingsRepository() {
        return new AdminProjectSettingsRepository();
    }

    @Bean
    @Primary
    AdminProjectTagVocabulary adminProjectTagVocabulary() {
        return new AdminProjectTagVocabulary();
    }

    @Bean
    @Primary
    UpdateDiscoverableResourcePort adminProjectUpdateDiscoverableResourcePort() {
        return new UpdateDiscoverableResourcePort() {
            @Override
            public DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input) {
                return new DiscoverableResourceProjectionResult.Updated();
            }
        };
    }

    @Bean
    @Primary
    RemoveDiscoverableResourcePort adminProjectRemoveDiscoverableResourcePort() {
        return new RemoveDiscoverableResourcePort() {
            @Override
            public DiscoverableResourceProjectionResult remove(RemoveDiscoverableResourceCommand command) {
                return new DiscoverableResourceProjectionResult.Noop();
            }
        };
    }

    static final class AdminProjectTestRepository implements ProjectRepository, ProjectAdminReadModel {
        private final Map<ProjectId, Project> values = new LinkedHashMap<>();

        void reset() {
            values.clear();
        }

        List<Project> all() {
            return List.copyOf(values.values());
        }

        @Override
        public Project save(Project project) {
            values.put(project.id(), project);
            return project;
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<Project> findBySlug(ProjectSlug slug, ContentLanguage language) {
            return values.values().stream()
                    .filter(project -> project.localizations().stream()
                            .anyMatch(localization -> localization.language() == language
                                    && localization.slug().equals(slug)))
                    .findFirst();
        }

        @Override
        public boolean existsSlug(ProjectSlug slug, ContentLanguage language) {
            return findBySlug(slug, language).isPresent();
        }

        @Override
        public List<AdminProjectListItem> list(ContentLanguage defaultLanguage) {
            return values.values().stream()
                    .map(project -> new AdminProjectListItem(
                            project.id().value(),
                            project.localizations().stream()
                                    .filter(localization -> localization.language() == defaultLanguage)
                                    .findFirst()
                                    .orElse(project.localizations().getFirst())
                                    .title().value(),
                            project.status().name(),
                            project.visibility().name(),
                            project.featured(),
                            project.sortOrder().map(sort -> sort.value()).orElse(null),
                            project.updatedAt()))
                    .toList();
        }

        @Override
        public Optional<ProjectAdminDetails> findDetails(ProjectId projectId) {
            return findById(projectId).map(project -> new ProjectAdminDetails(
                    project.id().value(),
                    project.status().name(),
                    project.visibility().name(),
                    project.featured(),
                    project.sortOrder().map(sort -> sort.value()).orElse(null),
                    project.tagIds(),
                    project.localizations().stream()
                            .map(localization -> new AdminProjectLocalizationView(
                                    localization.language().name(),
                                    localization.slug().value(),
                                    localization.title().value(),
                                    localization.summary().value(),
                                    localization.sections().stream()
                                            .map(section -> new AdminProjectCaseStudySectionView(
                                                    section.type().name(),
                                                    section.body().value(),
                                                    section.sortOrder().value()))
                                            .toList()))
                            .toList(),
                    project.technologies().stream()
                            .map(technology -> new AdminProjectTechnologyView(
                                    technology.name().value(),
                                    technology.category().name(),
                                    technology.sortOrder().value()))
                            .toList(),
                    project.links().stream()
                            .map(link -> new AdminProjectLinkView(
                                    link.label().value(),
                                    link.url().value(),
                                    link.linkType().name(),
                                    link.sortOrder().value()))
                            .toList(),
                    project.updatedAt(),
                    project.version().value()));
        }
    }

    static final class AdminProjectSettingsRepository implements SitePresentationSettingsRepository {
        private SitePresentationSettings current;

        AdminProjectSettingsRepository() {
            reset();
        }

        void reset() {
            current = SitePresentationSettings.create(
                    SitePresentationSettingsId.newId(),
                    SiteName.of("Seeded Site"),
                    ContentLanguage.TR,
                    Set.of(ContentLanguage.TR, ContentLanguage.EN),
                    null,
                    null,
                    null,
                    ThemePreference.SYSTEM,
                    HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                    Instant.parse("2026-06-16T10:00:00Z"));
        }

        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            current = settings;
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return Optional.ofNullable(current).filter(settings -> settings.id().equals(id));
        }
    }

    static final class AdminProjectTagVocabulary implements ProjectTagVocabularyPort {
        private final Map<TagId, ProjectTagDetails> tags = new LinkedHashMap<>();

        AdminProjectTagVocabulary() {
            TagId active = TagId.newId();
            tags.put(active, new ProjectTagDetails(active.value(), "Java", "java", false));
        }

        void reset() {
            tags.entrySet().removeIf(entry -> entry.getValue().archived());
        }

        TagId activeTagId() {
            return tags.entrySet().stream()
                    .filter(entry -> !entry.getValue().archived())
                    .map(entry -> entry.getKey())
                    .findFirst()
                    .orElseThrow();
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
        public ProjectTagAssignmentValidation validateAssignments(Set<TagId> current, Set<TagId> requested) {
            Set<TagId> missing = new LinkedHashSet<>(requested);
            missing.removeAll(tags.keySet());
            Set<TagId> newlyArchived = requested.stream()
                    .filter(id -> tags.containsKey(id) && tags.get(id).archived() && !current.contains(id))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return new ProjectTagAssignmentValidation(missing, newlyArchived);
        }
    }
}

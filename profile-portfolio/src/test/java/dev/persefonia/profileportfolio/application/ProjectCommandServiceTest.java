package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectLocalizationInput;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.discovery.ConfiguredProjectCanonicalUrlFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectDiscoverabilityCoordinator;
import dev.persefonia.profileportfolio.application.discovery.ProjectDiscoveryProjectionFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.exception.ProjectCommandRejectedException;
import dev.persefonia.profileportfolio.application.port.ProjectTagAssignmentValidation;
import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.service.ProjectCommandService;
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
import java.util.UUID;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import org.junit.jupiter.api.Test;

class ProjectCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");
    private static final PortfolioCommandActor OWNER = new PortfolioCommandActor(UUID.randomUUID(), true, true);
    private static final PortfolioCommandActor EDITOR = new PortfolioCommandActor(UUID.randomUUID(), true, false);

    private final Projects projects = new Projects();
    private final Vocabulary vocabulary = new Vocabulary();
    private final RecordingUpdatePort updatePort = new RecordingUpdatePort();
    private final RecordingRemovePort removePort = new RecordingRemovePort();

    @Test
    void ownerCreatesProjectUsingConfiguredDefaultLanguageForFeaturedValidation() {
        var service = service(ContentLanguage.EN);

        var result = service.create(create(OWNER, true, "PUBLIC", Set.of(), "EN"));

        assertThat(result.created()).isTrue();
        ProjectId resultId = ProjectId.from(result.projectId());
        assertThat(projects.values.get(resultId).featured()).isTrue();
        assertThat(projects.values.get(resultId).localizations())
                .extracting(localization -> localization.language())
                .containsExactly(ContentLanguage.EN);
    }

    @Test
    void createPublicProjectSyncsProjectDiscoveryProjection() {
        var service = service(ContentLanguage.EN);

        service.create(create(OWNER, true, "PUBLIC", Set.of(), "EN"));

        assertThat(removePort.commands).hasSize(1);
        assertThat(updatePort.inputs).hasSize(1);
        assertThat(updatePort.inputs.getFirst().publicUrl().value()).isEqualTo("/en/projects/en-project");
        assertThat(updatePort.inputs.getFirst().canonicalUrl().value())
                .isEqualTo("https://example.test/en/projects/en-project");
    }

    @Test
    void createUnlistedProjectSyncsDirectDetailProjection() {
        var service = service(ContentLanguage.TR);

        service.create(create(OWNER, false, "UNLISTED", Set.of(), "TR"));

        assertThat(removePort.commands).hasSize(1);
        assertThat(updatePort.inputs).hasSize(1);
        assertThat(updatePort.inputs.getFirst().publicUrl().value()).isEqualTo("/tr/projects/tr-project");
    }

    @Test
    void createPrivateProjectRemovesProjectDiscoveryProjectionWithoutUpdate() {
        var service = service(ContentLanguage.TR);

        service.create(create(OWNER, false, "PRIVATE", Set.of(), "TR"));

        assertThat(removePort.commands).hasSize(1);
        assertThat(updatePort.inputs).isEmpty();
    }

    @Test
    void updatePublicProjectToPrivateRemovesProjectionWithoutRecreate() {
        var service = service(ContentLanguage.TR);
        ProjectId projectId = ProjectId.from(service.create(create(OWNER, false, "PUBLIC", Set.of(), "TR")).projectId());
        updatePort.inputs.clear();
        removePort.commands.clear();

        service.update(update(projectId, Set.of()));

        assertThat(removePort.commands).hasSize(1);
        assertThat(updatePort.inputs).isEmpty();
    }

    @Test
    void syncFailureFailsCommand() {
        var service = service(ContentLanguage.TR);
        updatePort.reject = true;

        assertThatThrownBy(() -> service.create(create(OWNER, false, "PUBLIC", Set.of(), "TR")))
                .isInstanceOf(dev.persefonia.profileportfolio.application.exception.ProjectDiscoverySynchronizationException.class);
    }

    @Test
    void nonOwnerCannotCreateProject() {
        var service = service(ContentLanguage.TR);

        assertThatThrownBy(() -> service.create(create(EDITOR, false, "PRIVATE", Set.of(), "TR")))
                .isInstanceOf(SecurityException.class);
        assertThat(projects.values).isEmpty();
    }

    @Test
    void missingAndNewArchivedTagsAreRejectedBeforeSave() {
        var service = service(ContentLanguage.TR);
        TagId missing = TagId.newId();

        assertThatThrownBy(() -> service.create(create(OWNER, false, "PRIVATE", Set.of(missing), "TR")))
                .isInstanceOf(ProjectCommandRejectedException.class)
                .extracting(exception -> ((ProjectCommandRejectedException) exception).reason())
                .isEqualTo(ProjectCommandRejectedException.Reason.MISSING_TAG);

        TagId archived = vocabulary.archived("Archived");
        assertThatThrownBy(() -> service.create(create(OWNER, false, "PRIVATE", Set.of(archived), "TR")))
                .isInstanceOf(ProjectCommandRejectedException.class)
                .extracting(exception -> ((ProjectCommandRejectedException) exception).reason())
                .isEqualTo(ProjectCommandRejectedException.Reason.ARCHIVED_TAG);

        assertThat(projects.values).isEmpty();
    }

    @Test
    void currentlyAssignedArchivedTagMayRemainOrBeRemoved() {
        var service = service(ContentLanguage.TR);
        TagId archived = vocabulary.archived("Archived");
        ProjectId projectId = ProjectId.from(service.create(create(OWNER, false, "PRIVATE", Set.of(), "TR")).projectId());
        projects.values.get(projectId).replaceTags(Set.of(archived), ContentLanguage.TR, NOW.plusSeconds(1));

        service.update(update(projectId, Set.of(archived)));
        assertThat(projects.values.get(projectId).tagIds()).containsExactly(archived);

        service.update(update(projectId, Set.of()));
        assertThat(projects.values.get(projectId).tagIds()).isEmpty();
    }

    private ProjectCommandService service(ContentLanguage defaultLanguage) {
        return new ProjectCommandService(
                projects,
                new Settings(settings(defaultLanguage)),
                vocabulary,
                new TestAuthorizationPolicy(),
                new ProjectDiscoverabilityCoordinator(
                        updatePort,
                        removePort,
                        new ProjectDiscoveryProjectionFactory(
                                new ProjectPublicRouteFactory(),
                                new ConfiguredProjectCanonicalUrlFactory("https://example.test"))));
    }

    private static CreateProjectCommand create(
            PortfolioCommandActor actor,
            boolean featured,
            String visibility,
            Set<TagId> tagIds,
            String language) {
        return new CreateProjectCommand(
                actor,
                "ACTIVE",
                visibility,
                featured,
                null,
                tagIds.stream().map(TagId::value).collect(java.util.stream.Collectors.toSet()),
                List.of(localization(language)),
                List.of(),
                List.of(),
                NOW);
    }

    private static UpdateProjectCommand update(ProjectId projectId, Set<TagId> tagIds) {
        return new UpdateProjectCommand(
                OWNER,
                projectId.value(),
                "ACTIVE",
                "PRIVATE",
                false,
                null,
                tagIds.stream().map(TagId::value).collect(java.util.stream.Collectors.toSet()),
                List.of(localization("TR")),
                List.of(),
                List.of(),
                NOW.plusSeconds(2));
    }

    private static ProjectLocalizationInput localization(String language) {
        return new ProjectLocalizationInput(
                language,
                language.toLowerCase() + "-project",
                "Project " + language,
                "Summary",
                List.of());
    }

    private static SitePresentationSettings settings(ContentLanguage defaultLanguage) {
        return SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                defaultLanguage,
                Set.of(ContentLanguage.TR, ContentLanguage.EN),
                null,
                null,
                null,
                ThemePreference.SYSTEM,
                HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                NOW);
    }

    private static final class TestAuthorizationPolicy implements PortfolioCommandAuthorizationPolicy {
        @Override
        public void requireOwner(PortfolioCommandActor actor, String commandName) {
            if (!actor.active() || !actor.owner()) {
                throw new SecurityException(commandName);
            }
        }
    }

    private static final class Projects implements ProjectRepository {
        private final Map<ProjectId, Project> values = new LinkedHashMap<>();

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
    }

    private record Settings(SitePresentationSettings current) implements SitePresentationSettingsRepository {
        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            return Optional.of(current);
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return Optional.of(current).filter(settings -> settings.id().equals(id));
        }
    }

    private static final class Vocabulary implements ProjectTagVocabularyPort {
        private final Map<TagId, ProjectTagDetails> values = new LinkedHashMap<>();

        TagId archived(String name) {
            TagId id = TagId.newId();
            values.put(id, new ProjectTagDetails(id.value(), name, name.toLowerCase(), true));
            return id;
        }

        @Override
        public List<ProjectTagOption> findAssignableTags() {
            return List.of();
        }

        @Override
        public List<ProjectTagDetails> findByIds(Set<TagId> ids) {
            return ids.stream().map(values::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public ProjectTagAssignmentValidation validateAssignments(Set<TagId> current, Set<TagId> requested) {
            Set<TagId> missing = new LinkedHashSet<>(requested);
            missing.removeAll(values.keySet());
            Set<TagId> newlyArchived = requested.stream()
                    .filter(id -> values.containsKey(id) && values.get(id).archived() && !current.contains(id))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return new ProjectTagAssignmentValidation(missing, newlyArchived);
        }
    }

    private static final class RecordingUpdatePort implements UpdateDiscoverableResourcePort {
        private final List<DiscoverableResourceProjectionInput> inputs = new java.util.ArrayList<>();
        private boolean reject;

        @Override
        public DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input) {
            inputs.add(input);
            return reject
                    ? new DiscoverableResourceProjectionResult.Rejected(
                            DiscoverableResourceProjectionResult.Reason.CONFLICT)
                    : new DiscoverableResourceProjectionResult.Updated();
        }
    }

    private static final class RecordingRemovePort implements RemoveDiscoverableResourcePort {
        private final List<RemoveDiscoverableResourceCommand> commands = new java.util.ArrayList<>();

        @Override
        public DiscoverableResourceProjectionResult remove(RemoveDiscoverableResourceCommand command) {
            commands.add(command);
            return new DiscoverableResourceProjectionResult.Removed();
        }
    }
}

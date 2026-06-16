package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.CurrentFocusItemInput;
import dev.persefonia.profileportfolio.application.command.EducationSummaryInput;
import dev.persefonia.profileportfolio.application.command.ExternalProfileLinkInput;
import dev.persefonia.profileportfolio.application.command.ProfileLocalizationInput;
import dev.persefonia.profileportfolio.application.command.TechnicalFocusAreaInput;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.exception.PersonalProfileApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProfileCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");
    private static final PortfolioCommandActor OWNER = new PortfolioCommandActor(UUID.randomUUID(), true, true);
    private static final PortfolioCommandActor EDITOR = new PortfolioCommandActor(UUID.randomUUID(), true, false);
    private static final PortfolioCommandActor INACTIVE_OWNER = new PortfolioCommandActor(UUID.randomUUID(), false, true);

    @Test
    void ownerCreatesActiveProfileWhenNoneExists() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        var service = service(profiles, settings(ContentLanguage.TR));

        var result = service.upsertActive(command(OWNER, "Enes", "TR"));

        assertThat(result.created()).isTrue();
        assertThat(profiles.current).isNotNull();
        assertThat(profiles.current.active()).isTrue();
        assertThat(profiles.current.displayName().value()).isEqualTo("Enes");
        assertThat(profiles.current.localizations()).hasSize(1);
        assertThat(profiles.current.externalLinks()).hasSize(1);
    }

    @Test
    void ownerUpdatesExistingActiveProfile() {
        FakeProfileRepository profiles = new FakeProfileRepository();
        var service = service(profiles, settings(ContentLanguage.TR));
        service.upsertActive(command(OWNER, "Enes", "TR"));

        var result = service.upsertActive(command(OWNER, "Updated", "TR"));

        assertThat(result.created()).isFalse();
        assertThat(profiles.current.displayName().value()).isEqualTo("Updated");
        assertThat(profiles.current.version().value()).isEqualTo(1);
    }

    @Test
    void nonOwnerCannotUpsert() {
        var service = service(new FakeProfileRepository(), settings(ContentLanguage.TR));

        assertThatThrownBy(() -> service.upsertActive(command(EDITOR, "Enes", "TR")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void inactiveOwnerCannotUpsert() {
        var service = service(new FakeProfileRepository(), settings(ContentLanguage.TR));

        assertThatThrownBy(() -> service.upsertActive(command(INACTIVE_OWNER, "Enes", "TR")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void defaultLanguageLocalizationIsRequired() {
        var service = service(new FakeProfileRepository(), settings(ContentLanguage.EN));

        assertThatThrownBy(() -> service.upsertActive(command(OWNER, "Enes", "TR")))
                .isInstanceOf(PersonalProfileApplicationException.class)
                .hasMessageContaining("Default-language");
    }

    @Test
    void missingSettingsFailsExplicitly() {
        var service = service(new FakeProfileRepository(), null);

        assertThatThrownBy(() -> service.upsertActive(command(OWNER, "Enes", "TR")))
                .isInstanceOf(SitePresentationSettingsNotInitializedException.class);
    }

    private static PersonalProfileCommandService service(
            FakeProfileRepository profiles,
            SitePresentationSettings settings) {
        return new PersonalProfileCommandService(
                profiles,
                new FakeSettingsRepository(settings),
                new TestAuthorizationPolicy());
    }

    private static UpsertActivePersonalProfileCommand command(
            PortfolioCommandActor actor,
            String displayName,
            String language) {
        return new UpsertActivePersonalProfileCommand(
                actor,
                displayName,
                List.of(new ProfileLocalizationInput(
                        language,
                        "Short bio",
                        "Long bio",
                        "Istanbul",
                        List.of(new TechnicalFocusAreaInput("Architecture", "Systems", 1)),
                        List.of(new EducationSummaryInput("University", "Computer Science", "Research", 1)),
                        List.of(new CurrentFocusItemInput("Building", 1)))),
                List.of(new ExternalProfileLinkInput("GitHub", "https://example.test", 1)),
                NOW);
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

    private static final class FakeProfileRepository implements PersonalProfileRepository {
        private PersonalProfile current;

        @Override
        public PersonalProfile save(PersonalProfile profile) {
            current = profile;
            return profile;
        }

        @Override
        public Optional<PersonalProfile> findById(ProfileId id) {
            return Optional.ofNullable(current).filter(profile -> profile.id().equals(id));
        }

        @Override
        public Optional<PersonalProfile> findActiveProfile() {
            return Optional.ofNullable(current).filter(PersonalProfile::active);
        }
    }

    private static final class FakeSettingsRepository implements SitePresentationSettingsRepository {
        private final SitePresentationSettings current;

        private FakeSettingsRepository(SitePresentationSettings current) {
            this.current = current;
        }

        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
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
}

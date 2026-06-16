package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SitePresentationSettingsCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");
    private static final PortfolioCommandActor OWNER = new PortfolioCommandActor(UUID.randomUUID(), true, true);
    private static final PortfolioCommandActor EDITOR = new PortfolioCommandActor(UUID.randomUUID(), true, false);
    private static final PortfolioCommandActor INACTIVE_OWNER = new PortfolioCommandActor(UUID.randomUUID(), false, true);

    @Test
    void ownerCanUpdateSettings() {
        FakeSettingsRepository repository = new FakeSettingsRepository(settings(null));
        var service = new SitePresentationSettingsCommandService(repository, new TestAuthorizationPolicy());

        var result = service.update(command(OWNER, "Portfolio", "EN", Set.of("TR", "EN"), "", "", "DARK"));

        assertThat(result.version()).isEqualTo(1);
        assertThat(repository.current.siteName().value()).isEqualTo("Portfolio");
        assertThat(repository.current.defaultLanguage()).isEqualTo(ContentLanguage.EN);
        assertThat(repository.current.defaultTheme()).isEqualTo(ThemePreference.DARK);
    }

    @Test
    void nonOwnerCannotUpdateSettings() {
        var service = new SitePresentationSettingsCommandService(
                new FakeSettingsRepository(settings(null)), new TestAuthorizationPolicy());

        assertThatThrownBy(() -> service.update(command(EDITOR, "Portfolio", "TR", Set.of("TR"), "", "", "SYSTEM")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void inactiveActorCannotUpdateSettings() {
        var service = new SitePresentationSettingsCommandService(
                new FakeSettingsRepository(settings(null)), new TestAuthorizationPolicy());

        assertThatThrownBy(() -> service.update(command(INACTIVE_OWNER, "Portfolio", "TR", Set.of("TR"), "", "", "SYSTEM")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void updatePreservesDefaultOpenGraphImageAssetId() {
        AssetId assetId = AssetId.newId();
        FakeSettingsRepository repository = new FakeSettingsRepository(settings(assetId));
        var service = new SitePresentationSettingsCommandService(repository, new TestAuthorizationPolicy());

        service.update(command(OWNER, "Portfolio", "TR", Set.of("TR"), "", "", "LIGHT"));

        assertThat(repository.current.defaultOpenGraphImageAssetId()).hasValue(assetId);
    }

    @Test
    void blankOptionalFieldsAreHandledSafely() {
        FakeSettingsRepository repository = new FakeSettingsRepository(settings(null));
        var service = new SitePresentationSettingsCommandService(repository, new TestAuthorizationPolicy());

        service.update(command(OWNER, "Portfolio", "TR", Set.of("TR"), " ", " ", "SYSTEM"));

        assertThat(repository.current.titleSuffix()).isEmpty();
        assertThat(repository.current.defaultMetaDescription()).isEmpty();
    }

    @Test
    void missingSettingsSingletonThrowsExplicitApplicationException() {
        var service = new SitePresentationSettingsCommandService(new FakeSettingsRepository(null), new TestAuthorizationPolicy());

        assertThatThrownBy(() -> service.update(command(OWNER, "Portfolio", "TR", Set.of("TR"), "", "", "SYSTEM")))
                .isInstanceOf(SitePresentationSettingsNotInitializedException.class);
    }

    @Test
    void invalidCommandInputThrowsApplicationException() {
        var service = new SitePresentationSettingsCommandService(
                new FakeSettingsRepository(settings(null)), new TestAuthorizationPolicy());

        assertThatThrownBy(() -> service.update(command(OWNER, " ", "TR", Set.of("TR"), "", "", "SYSTEM")))
                .isInstanceOf(SitePresentationSettingsApplicationException.class);
    }

    private static UpdateSitePresentationSettingsCommand command(
            PortfolioCommandActor actor,
            String siteName,
            String defaultLanguage,
            Set<String> supportedLanguages,
            String titleSuffix,
            String description,
            String theme) {
        return new UpdateSitePresentationSettingsCommand(
                actor,
                siteName,
                defaultLanguage,
                supportedLanguages,
                titleSuffix,
                description,
                theme,
                true,
                true,
                false,
                3,
                5,
                NOW);
    }

    private static SitePresentationSettings settings(AssetId assetId) {
        return SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR),
                null,
                null,
                assetId,
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

    private static final class FakeSettingsRepository implements SitePresentationSettingsRepository {
        private SitePresentationSettings current;

        private FakeSettingsRepository(SitePresentationSettings current) {
            this.current = current;
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
}

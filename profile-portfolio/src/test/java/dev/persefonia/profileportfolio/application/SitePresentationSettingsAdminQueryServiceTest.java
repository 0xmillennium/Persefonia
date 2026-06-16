package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsAdminQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SitePresentationSettingsAdminQueryServiceTest {
    @Test
    void returnsCurrentSettingsView() {
        var service = new SitePresentationSettingsAdminQueryService(new FakeSettingsRepository(settings()));

        var view = service.current();

        assertThat(view.siteName()).isEqualTo("Persefonia");
        assertThat(view.defaultLanguage()).isEqualTo("TR");
        assertThat(view.supportedLanguages()).containsExactlyInAnyOrder("TR", "EN");
        assertThat(view.titleSuffix()).isEqualTo("| Notes");
        assertThat(view.defaultMetaDescription()).isEqualTo("Software notes.");
        assertThat(view.defaultTheme()).isEqualTo("SYSTEM");
        assertThat(view.featuredProjectLimit()).isEqualTo(3);
        assertThat(view.latestWritingLimit()).isEqualTo(5);
    }

    @Test
    void missingSettingsSingletonThrowsExplicitApplicationException() {
        var service = new SitePresentationSettingsAdminQueryService(new FakeSettingsRepository(null));

        assertThatThrownBy(service::current).isInstanceOf(SitePresentationSettingsNotInitializedException.class);
    }

    private static SitePresentationSettings settings() {
        return SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR, ContentLanguage.EN),
                TitleSuffix.of("| Notes"),
                SeoDescription.of("Software notes."),
                null,
                ThemePreference.SYSTEM,
                HomepageSettings.of(true, true, false, PositiveInteger.of(3), PositiveInteger.of(5)),
                Instant.parse("2026-06-16T10:00:00Z"));
    }

    private record FakeSettingsRepository(SitePresentationSettings current) implements SitePresentationSettingsRepository {
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

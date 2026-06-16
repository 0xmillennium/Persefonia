package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
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

class PublicHomepageSettingsQueryServiceTest {
    @Test
    void returnsHomepageSettingsWithoutAdminActor() {
        var service = new PublicHomepageSettingsQueryService(new FakeSettingsRepository(settings()));

        var view = service.current();

        assertThat(view.siteName()).isEqualTo("Persefonia");
        assertThat(view.defaultLanguage()).isEqualTo("EN");
        assertThat(view.titleSuffix()).isEqualTo("| Portfolio");
        assertThat(view.defaultMetaDescription()).isEqualTo("Portfolio homepage.");
        assertThat(view.defaultTheme()).isEqualTo("LIGHT");
        assertThat(view.showFeaturedProjects()).isTrue();
        assertThat(view.featuredProjectLimit()).isEqualTo(2);
    }

    @Test
    void doesNotRequireProfileOrProjectData() {
        var repository = new FakeSettingsRepository(settings());
        var service = new PublicHomepageSettingsQueryService(repository);

        service.current();

        assertThat(repository.findCurrentCalls).isEqualTo(1);
    }

    @Test
    void missingSettingsSingletonThrowsExplicitApplicationException() {
        var service = new PublicHomepageSettingsQueryService(new FakeSettingsRepository(null));

        assertThatThrownBy(service::current).isInstanceOf(SitePresentationSettingsNotInitializedException.class);
    }

    private static SitePresentationSettings settings() {
        return SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.EN,
                Set.of(ContentLanguage.EN),
                TitleSuffix.of("| Portfolio"),
                SeoDescription.of("Portfolio homepage."),
                null,
                ThemePreference.LIGHT,
                HomepageSettings.of(true, false, false, PositiveInteger.of(2), PositiveInteger.of(4)),
                Instant.parse("2026-06-16T10:00:00Z"));
    }

    private static final class FakeSettingsRepository implements SitePresentationSettingsRepository {
        private final SitePresentationSettings current;
        private int findCurrentCalls;

        private FakeSettingsRepository(SitePresentationSettings current) {
            this.current = current;
        }

        @Override
        public SitePresentationSettings save(SitePresentationSettings settings) {
            return settings;
        }

        @Override
        public Optional<SitePresentationSettings> findCurrent() {
            findCurrentCalls++;
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<SitePresentationSettings> findById(SitePresentationSettingsId id) {
            return Optional.ofNullable(current).filter(settings -> settings.id().equals(id));
        }
    }
}

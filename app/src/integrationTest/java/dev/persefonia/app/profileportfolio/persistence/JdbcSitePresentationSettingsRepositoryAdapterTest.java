package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsId;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcSitePresentationSettingsRepositoryAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final SitePresentationSettingsId SEED_ID =
            SitePresentationSettingsId.from(UUID.fromString("00000000-0000-0000-0000-000000000701"));

    @Test
    void findCurrentReturnsSeededSettingsWithSupportedLanguages() {
        SitePresentationSettings current = settings.findCurrent().orElseThrow();

        assertThat(current.id()).isEqualTo(SEED_ID);
        assertThat(current.supportedLanguages()).containsExactlyInAnyOrder(ContentLanguage.TR, ContentLanguage.EN);
    }

    @Test
    void findByIdReturnsSeededSettings() {
        assertThat(settings.findById(SEED_ID)).isPresent();
    }

    @Test
    void saveUpdatesSettingsAndUsesDomainVersion() {
        SitePresentationSettings current = settings.findCurrent().orElseThrow();
        current.changeTheme(ThemePreference.DARK, Instant.parse("2026-06-16T11:00:00Z"));

        SitePresentationSettings saved = settings.save(current);

        assertThat(saved.defaultTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(saved.version().value()).isEqualTo(1);
    }
}

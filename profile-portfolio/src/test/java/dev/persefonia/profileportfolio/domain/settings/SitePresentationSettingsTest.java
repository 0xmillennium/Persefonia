package dev.persefonia.profileportfolio.domain.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SitePresentationSettingsTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void rejectsBlankSiteName() {
        assertThatThrownBy(() -> SiteName.of(" "))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsEmptySupportedLanguages() {
        assertThatThrownBy(() -> settings(Set.of(), ContentLanguage.TR, homepage(3, 5)))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDefaultLanguageNotInSupportedLanguages() {
        assertThatThrownBy(() -> settings(Set.of(ContentLanguage.EN), ContentLanguage.TR, homepage(3, 5)))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsNonPositiveFeaturedProjectLimit() {
        assertThatThrownBy(() -> homepage(0, 5))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsNonPositiveLatestWritingLimit() {
        assertThatThrownBy(() -> homepage(3, 0))
                .isInstanceOf(PortfolioValidationException.class);
    }

    private static SitePresentationSettings settings(
            Set<ContentLanguage> supportedLanguages,
            ContentLanguage defaultLanguage,
            HomepageSettings homepageSettings) {
        return SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                defaultLanguage,
                supportedLanguages,
                null,
                null,
                null,
                ThemePreference.SYSTEM,
                homepageSettings,
                NOW);
    }

    private static HomepageSettings homepage(int featuredLimit, int writingLimit) {
        return HomepageSettings.of(
                true,
                true,
                false,
                PositiveInteger.of(featuredLimit),
                PositiveInteger.of(writingLimit));
    }
}

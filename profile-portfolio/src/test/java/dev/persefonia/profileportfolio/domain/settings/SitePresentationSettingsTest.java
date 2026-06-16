package dev.persefonia.profileportfolio.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SitePresentationSettingsTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-16T11:00:00Z");

    @Test
    void atomicUpdateChangesPresentationThemeAndHomepageSettings() {
        SitePresentationSettings settings = settings(Set.of(ContentLanguage.TR), ContentLanguage.TR, homepage(3, 5));

        settings.updateSettings(
                SiteName.of("Portfolio"),
                ContentLanguage.EN,
                Set.of(ContentLanguage.EN, ContentLanguage.TR),
                TitleSuffix.of("| Works"),
                SeoDescription.of("Independent software work."),
                ThemePreference.DARK,
                homepage(2, 4),
                LATER);

        assertThat(settings.siteName().value()).isEqualTo("Portfolio");
        assertThat(settings.defaultLanguage()).isEqualTo(ContentLanguage.EN);
        assertThat(settings.supportedLanguages()).containsExactlyInAnyOrder(ContentLanguage.EN, ContentLanguage.TR);
        assertThat(settings.titleSuffix()).hasValue(TitleSuffix.of("| Works"));
        assertThat(settings.defaultMetaDescription()).hasValue(SeoDescription.of("Independent software work."));
        assertThat(settings.defaultTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(settings.homepageSettings().featuredProjectLimit()).isEqualTo(PositiveInteger.of(2));
        assertThat(settings.homepageSettings().latestWritingLimit()).isEqualTo(PositiveInteger.of(4));
    }

    @Test
    void atomicUpdateIncrementsVersionOnceAndChangesUpdatedAtOnce() {
        SitePresentationSettings settings = settings(Set.of(ContentLanguage.TR), ContentLanguage.TR, homepage(3, 5));

        settings.updateSettings(
                SiteName.of("Portfolio"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR),
                null,
                null,
                ThemePreference.LIGHT,
                homepage(1, 2),
                LATER);

        assertThat(settings.version().value()).isEqualTo(1);
        assertThat(settings.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void atomicUpdateRejectsDefaultLanguageOutsideSupportedLanguages() {
        SitePresentationSettings settings = settings(Set.of(ContentLanguage.TR), ContentLanguage.TR, homepage(3, 5));

        assertThatThrownBy(() -> settings.updateSettings(
                        SiteName.of("Portfolio"),
                        ContentLanguage.EN,
                        Set.of(ContentLanguage.TR),
                        null,
                        null,
                        ThemePreference.SYSTEM,
                        homepage(3, 5),
                        LATER))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void atomicUpdatePreservesDefaultOpenGraphImageAssetId() {
        AssetId assetId = AssetId.newId();
        SitePresentationSettings settings = SitePresentationSettings.create(
                SitePresentationSettingsId.newId(),
                SiteName.of("Persefonia"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR),
                null,
                null,
                assetId,
                ThemePreference.SYSTEM,
                homepage(3, 5),
                NOW);

        settings.updateSettings(
                SiteName.of("Portfolio"),
                ContentLanguage.TR,
                Set.of(ContentLanguage.TR),
                null,
                null,
                ThemePreference.DARK,
                homepage(4, 6),
                LATER);

        assertThat(settings.defaultOpenGraphImageAssetId()).hasValue(assetId);
    }

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

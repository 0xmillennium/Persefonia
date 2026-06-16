package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SitePresentationSettings {
    private final SitePresentationSettingsId id;
    private SiteName siteName;
    private ContentLanguage defaultLanguage;
    private Set<ContentLanguage> supportedLanguages;
    private TitleSuffix titleSuffix;
    private SeoDescription defaultMetaDescription;
    private AssetId defaultOpenGraphImageAssetId;
    private ThemePreference defaultTheme;
    private HomepageSettings homepageSettings;
    private Instant updatedAt;
    private Version version;

    private SitePresentationSettings(
            SitePresentationSettingsId id,
            SiteName siteName,
            ContentLanguage defaultLanguage,
            Set<ContentLanguage> supportedLanguages,
            TitleSuffix titleSuffix,
            SeoDescription defaultMetaDescription,
            AssetId defaultOpenGraphImageAssetId,
            ThemePreference defaultTheme,
            HomepageSettings homepageSettings,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.siteName = Objects.requireNonNull(siteName, "siteName");
        this.defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        this.supportedLanguages = Set.copyOf(Objects.requireNonNull(supportedLanguages, "supportedLanguages"));
        this.titleSuffix = titleSuffix;
        this.defaultMetaDescription = defaultMetaDescription;
        this.defaultOpenGraphImageAssetId = defaultOpenGraphImageAssetId;
        this.defaultTheme = Objects.requireNonNull(defaultTheme, "defaultTheme");
        this.homepageSettings = Objects.requireNonNull(homepageSettings, "homepageSettings");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        validateLanguages(this.defaultLanguage, this.supportedLanguages);
    }

    public static SitePresentationSettings create(
            SitePresentationSettingsId id,
            SiteName siteName,
            ContentLanguage defaultLanguage,
            Set<ContentLanguage> supportedLanguages,
            TitleSuffix titleSuffix,
            SeoDescription defaultMetaDescription,
            AssetId defaultOpenGraphImageAssetId,
            ThemePreference defaultTheme,
            HomepageSettings homepageSettings,
            Instant now) {
        return new SitePresentationSettings(
                id,
                siteName,
                defaultLanguage,
                supportedLanguages,
                titleSuffix,
                defaultMetaDescription,
                defaultOpenGraphImageAssetId,
                defaultTheme,
                homepageSettings,
                now,
                Version.initial());
    }

    public static SitePresentationSettings rehydrate(
            SitePresentationSettingsId id,
            SiteName siteName,
            ContentLanguage defaultLanguage,
            Set<ContentLanguage> supportedLanguages,
            TitleSuffix titleSuffix,
            SeoDescription defaultMetaDescription,
            AssetId defaultOpenGraphImageAssetId,
            ThemePreference defaultTheme,
            HomepageSettings homepageSettings,
            Instant updatedAt,
            Version version) {
        return new SitePresentationSettings(
                id,
                siteName,
                defaultLanguage,
                supportedLanguages,
                titleSuffix,
                defaultMetaDescription,
                defaultOpenGraphImageAssetId,
                defaultTheme,
                homepageSettings,
                updatedAt,
                version);
    }

    public void changePresentation(
            SiteName siteName,
            ContentLanguage defaultLanguage,
            TitleSuffix titleSuffix,
            SeoDescription defaultMetaDescription,
            AssetId defaultOpenGraphImageAssetId,
            Instant now) {
        this.siteName = Objects.requireNonNull(siteName, "siteName");
        this.defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        this.titleSuffix = titleSuffix;
        this.defaultMetaDescription = defaultMetaDescription;
        this.defaultOpenGraphImageAssetId = defaultOpenGraphImageAssetId;
        validateLanguages(this.defaultLanguage, this.supportedLanguages);
        markUpdated(now);
    }

    public void changeSupportedLanguages(Set<ContentLanguage> supportedLanguages, Instant now) {
        this.supportedLanguages = Set.copyOf(Objects.requireNonNull(supportedLanguages, "supportedLanguages"));
        validateLanguages(defaultLanguage, this.supportedLanguages);
        markUpdated(now);
    }

    public void changeHomepageSettings(HomepageSettings homepageSettings, Instant now) {
        this.homepageSettings = Objects.requireNonNull(homepageSettings, "homepageSettings");
        markUpdated(now);
    }

    public void changeTheme(ThemePreference defaultTheme, Instant now) {
        this.defaultTheme = Objects.requireNonNull(defaultTheme, "defaultTheme");
        markUpdated(now);
    }

    public void updateSettings(
            SiteName siteName,
            ContentLanguage defaultLanguage,
            Set<ContentLanguage> supportedLanguages,
            TitleSuffix titleSuffix,
            SeoDescription defaultMetaDescription,
            ThemePreference defaultTheme,
            HomepageSettings homepageSettings,
            Instant now) {
        Objects.requireNonNull(now, "now");
        Set<ContentLanguage> supportedLanguagesCopy =
                Set.copyOf(Objects.requireNonNull(supportedLanguages, "supportedLanguages"));
        validateLanguages(Objects.requireNonNull(defaultLanguage, "defaultLanguage"), supportedLanguagesCopy);
        this.siteName = Objects.requireNonNull(siteName, "siteName");
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = supportedLanguagesCopy;
        this.titleSuffix = titleSuffix;
        this.defaultMetaDescription = defaultMetaDescription;
        this.defaultTheme = Objects.requireNonNull(defaultTheme, "defaultTheme");
        this.homepageSettings = Objects.requireNonNull(homepageSettings, "homepageSettings");
        markUpdated(now);
    }

    private void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version = version.next();
    }

    private static void validateLanguages(ContentLanguage defaultLanguage, Set<ContentLanguage> supportedLanguages) {
        if (supportedLanguages.isEmpty()) {
            throw new PortfolioValidationException("supported languages must not be empty");
        }
        if (!supportedLanguages.contains(defaultLanguage)) {
            throw new PortfolioValidationException("default language must be supported");
        }
    }

    public SitePresentationSettingsId id() {
        return id;
    }

    public SiteName siteName() {
        return siteName;
    }

    public ContentLanguage defaultLanguage() {
        return defaultLanguage;
    }

    public Set<ContentLanguage> supportedLanguages() {
        return supportedLanguages;
    }

    public Optional<TitleSuffix> titleSuffix() {
        return Optional.ofNullable(titleSuffix);
    }

    public Optional<SeoDescription> defaultMetaDescription() {
        return Optional.ofNullable(defaultMetaDescription);
    }

    public Optional<AssetId> defaultOpenGraphImageAssetId() {
        return Optional.ofNullable(defaultOpenGraphImageAssetId);
    }

    public ThemePreference defaultTheme() {
        return defaultTheme;
    }

    public HomepageSettings homepageSettings() {
        return homepageSettings;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Version version() {
        return version;
    }
}

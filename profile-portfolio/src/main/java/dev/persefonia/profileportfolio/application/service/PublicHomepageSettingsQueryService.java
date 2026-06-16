package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.PublicHomepageSettingsView;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.util.Objects;

public final class PublicHomepageSettingsQueryService {
    private final SitePresentationSettingsRepository settings;

    public PublicHomepageSettingsQueryService(SitePresentationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public PublicHomepageSettingsView current() {
        return settings.findCurrent()
                .map(PublicHomepageSettingsQueryService::toView)
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
    }

    private static PublicHomepageSettingsView toView(SitePresentationSettings settings) {
        var homepage = settings.homepageSettings();
        return new PublicHomepageSettingsView(
                settings.siteName().value(),
                settings.defaultLanguage().name(),
                settings.titleSuffix().map(TitleSuffix::value).orElse(""),
                settings.defaultMetaDescription().map(SeoDescription::value).orElse(""),
                settings.defaultTheme().name(),
                homepage.showFeaturedProjects(),
                homepage.showLatestWriting(),
                homepage.showResearchHighlights(),
                homepage.featuredProjectLimit().value(),
                homepage.latestWritingLimit().value());
    }
}

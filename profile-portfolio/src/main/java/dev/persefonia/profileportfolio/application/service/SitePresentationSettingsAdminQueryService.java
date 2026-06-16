package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.AdminSitePresentationSettingsView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SitePresentationSettingsAdminQueryService {
    private final SitePresentationSettingsRepository settings;

    public SitePresentationSettingsAdminQueryService(SitePresentationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public AdminSitePresentationSettingsView current() {
        return settings.findCurrent()
                .map(SitePresentationSettingsAdminQueryService::toView)
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
    }

    private static AdminSitePresentationSettingsView toView(SitePresentationSettings settings) {
        var homepage = settings.homepageSettings();
        return new AdminSitePresentationSettingsView(
                settings.id().value(),
                settings.siteName().value(),
                settings.defaultLanguage().name(),
                settings.supportedLanguages().stream().map(ContentLanguage::name).collect(Collectors.toUnmodifiableSet()),
                settings.titleSuffix().map(TitleSuffix::value).orElse(""),
                settings.defaultMetaDescription().map(SeoDescription::value).orElse(""),
                settings.defaultTheme().name(),
                homepage.showFeaturedProjects(),
                homepage.showLatestWriting(),
                homepage.showResearchHighlights(),
                homepage.featuredProjectLimit().value(),
                homepage.latestWritingLimit().value(),
                settings.updatedAt(),
                settings.version().value());
    }
}

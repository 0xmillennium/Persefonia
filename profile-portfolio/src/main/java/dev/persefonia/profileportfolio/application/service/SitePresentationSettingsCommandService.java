package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.settings.HomepageSettings;
import dev.persefonia.profileportfolio.domain.settings.PositiveInteger;
import dev.persefonia.profileportfolio.domain.settings.SeoDescription;
import dev.persefonia.profileportfolio.domain.settings.SiteName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
import dev.persefonia.profileportfolio.domain.settings.TitleSuffix;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SitePresentationSettingsCommandService {
    private final SitePresentationSettingsRepository settings;
    private final PortfolioCommandAuthorizationPolicy authorization;

    public SitePresentationSettingsCommandService(
            SitePresentationSettingsRepository settings,
            PortfolioCommandAuthorizationPolicy authorization) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public SitePresentationSettingsUpdateResult update(UpdateSitePresentationSettingsCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "portfolio.settings.update");
        SitePresentationSettings current = settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
        try {
            current.updateSettings(
                    SiteName.of(command.siteName()),
                    language(command.defaultLanguage()),
                    languages(command.supportedLanguages()),
                    optionalTitleSuffix(command.titleSuffix()),
                    optionalSeoDescription(command.defaultMetaDescription()),
                    ThemePreference.valueOf(command.defaultTheme()),
                    HomepageSettings.of(
                            command.showFeaturedProjects(),
                            command.showLatestWriting(),
                            command.showResearchHighlights(),
                            PositiveInteger.of(command.featuredProjectLimit()),
                            PositiveInteger.of(command.latestWritingLimit())),
                    command.requestedAt());
            SitePresentationSettings saved = settings.save(current);
            return new SitePresentationSettingsUpdateResult(
                    saved.id().value(), saved.updatedAt(), saved.version().value());
        } catch (IllegalArgumentException | PortfolioValidationException exception) {
            throw new SitePresentationSettingsApplicationException(
                    "Site presentation settings update was rejected.", exception);
        }
    }

    private static ContentLanguage language(String value) {
        return ContentLanguage.valueOf(value);
    }

    private static Set<ContentLanguage> languages(Set<String> values) {
        return values.stream().map(SitePresentationSettingsCommandService::language).collect(Collectors.toUnmodifiableSet());
    }

    private static TitleSuffix optionalTitleSuffix(String value) {
        return isBlank(value) ? null : TitleSuffix.of(value);
    }

    private static SeoDescription optionalSeoDescription(String value) {
        return isBlank(value) ? null : SeoDescription.of(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

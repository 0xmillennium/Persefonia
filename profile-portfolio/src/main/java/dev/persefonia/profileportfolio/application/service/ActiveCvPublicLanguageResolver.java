package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class ActiveCvPublicLanguageResolver {
    private final SitePresentationSettingsRepository settings;

    ActiveCvPublicLanguageResolver(SitePresentationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    Optional<ContentLanguage> defaultLanguage() {
        return settings.findCurrent().map(setting -> setting.defaultLanguage());
    }

    Optional<ContentLanguage> explicitLanguage(String language) {
        if (language == null) {
            return Optional.empty();
        }
        ContentLanguage parsed = switch (language) {
            case "tr" -> ContentLanguage.TR;
            case "en" -> ContentLanguage.EN;
            default -> null;
        };
        if (parsed == null) {
            return Optional.empty();
        }
        return settings.findCurrent()
                .filter(setting -> setting.supportedLanguages().contains(parsed))
                .map(setting -> parsed);
    }

    static String routeCode(ContentLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    static String filename(ContentLanguage language) {
        return "cv-" + routeCode(language) + ".pdf";
    }
}

package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.StandardPublicThemeView;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Objects;

public final class StandardPublicThemeQueryService {
    private final SitePresentationSettingsRepository settings;

    public StandardPublicThemeQueryService(SitePresentationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public StandardPublicThemeView current() {
        return settings.findCurrent()
                .map(value -> switch (value.standardPublicTheme()) {
                    case LIGHT -> StandardPublicThemeView.LIGHT;
                    case DARK -> StandardPublicThemeView.DARK;
                    case SYSTEM -> StandardPublicThemeView.SYSTEM;
                })
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
    }
}

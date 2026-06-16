package dev.persefonia.profileportfolio.application.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AdminPersonalProfileView(
        String defaultLanguage,
        boolean profileExists,
        String displayName,
        List<AdminProfileLocalizationView> localizations,
        List<AdminExternalProfileLinkView> externalLinks) {
    public AdminPersonalProfileView {
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
    }

    public Optional<AdminProfileLocalizationView> localization(String language) {
        return localizations.stream()
                .filter(localization -> localization.language().equals(language))
                .findFirst();
    }
}

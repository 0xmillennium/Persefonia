package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.AdminCurrentFocusItemView;
import dev.persefonia.profileportfolio.application.query.AdminEducationSummaryView;
import dev.persefonia.profileportfolio.application.query.AdminExternalProfileLinkView;
import dev.persefonia.profileportfolio.application.query.AdminPersonalProfileView;
import dev.persefonia.profileportfolio.application.query.AdminProfileLocalizationView;
import dev.persefonia.profileportfolio.application.query.AdminTechnicalFocusAreaView;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PersonalProfileAdminQueryService {
    private final PersonalProfileRepository profiles;
    private final SitePresentationSettingsRepository settings;

    public PersonalProfileAdminQueryService(
            PersonalProfileRepository profiles,
            SitePresentationSettingsRepository settings) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public AdminPersonalProfileView current() {
        String defaultLanguage = settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new)
                .defaultLanguage()
                .name();
        return profiles.findActiveProfile()
                .map(profile -> toView(defaultLanguage, profile))
                .orElseGet(() -> new AdminPersonalProfileView(defaultLanguage, false, "", List.of(), List.of()));
    }

    private static AdminPersonalProfileView toView(String defaultLanguage, PersonalProfile profile) {
        return new AdminPersonalProfileView(
                defaultLanguage,
                true,
                profile.displayName().value(),
                profile.localizations().stream()
                        .sorted(Comparator.comparing(localization -> localization.language().name()))
                        .map(PersonalProfileAdminQueryService::localization)
                        .toList(),
                profile.externalLinks().stream()
                        .sorted(Comparator.comparing(link -> link.sortOrder().value()))
                        .map(link -> new AdminExternalProfileLinkView(
                                link.label().value(), link.url().value(), link.sortOrder().value()))
                        .toList());
    }

    private static AdminProfileLocalizationView localization(ProfileLocalization localization) {
        return new AdminProfileLocalizationView(
                localization.language().name(),
                localization.shortBio().value(),
                localization.longBio().value(),
                localization.locationText() == null ? "" : localization.locationText().value(),
                localization.technicalFocusAreas().stream()
                        .sorted(Comparator.comparing(area -> area.sortOrder().value()))
                        .map(area -> new AdminTechnicalFocusAreaView(
                                area.name().value(),
                                area.description() == null ? "" : area.description().value(),
                                area.sortOrder().value()))
                        .toList(),
                localization.educationSummaries().stream()
                        .sorted(Comparator.comparing(summary -> summary.sortOrder().value()))
                        .map(summary -> new AdminEducationSummaryView(
                                summary.institution().value(),
                                summary.program().value(),
                                summary.description() == null ? "" : summary.description().value(),
                                summary.sortOrder().value()))
                        .toList(),
                localization.currentFocusItems().stream()
                        .sorted(Comparator.comparing(item -> item.sortOrder().value()))
                        .map(item -> new AdminCurrentFocusItemView(item.text().value(), item.sortOrder().value()))
                        .toList());
    }
}

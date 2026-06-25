package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.query.PublicCurrentFocusItemView;
import dev.persefonia.profileportfolio.application.query.PublicEducationSummaryView;
import dev.persefonia.profileportfolio.application.query.PublicProfileExternalLinkView;
import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
import dev.persefonia.profileportfolio.application.query.PublicTechnicalFocusAreaView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class PublicProfileSummaryQueryService {
    private final PersonalProfileRepository profiles;

    public PublicProfileSummaryQueryService(PersonalProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public Optional<PublicProfileSummaryView> currentSummary(ContentLanguage language) {
        Objects.requireNonNull(language, "language");
        return profiles.findActiveProfile()
                .flatMap(profile -> summary(profile, language));
    }

    public Optional<PublicProfileSummaryView> currentSummary(String language) {
        try {
            return currentSummary(ContentLanguage.valueOf(language));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<PublicProfileSummaryView> summary(
            PersonalProfile profile,
            ContentLanguage language) {
        return profile.localizations().stream()
                .filter(localization -> localization.language() == language)
                .findFirst()
                .map(localization -> summary(profile, localization));
    }

    private static PublicProfileSummaryView summary(PersonalProfile profile, ProfileLocalization localization) {
        return new PublicProfileSummaryView(
                profile.displayName().value(),
                localization.shortBio().value(),
                localization.locationText() == null ? "" : localization.locationText().value(),
                profile.externalLinks().stream()
                        .sorted(Comparator.comparing(link -> link.sortOrder().value()))
                        .map(link -> new PublicProfileExternalLinkView(
                                link.label().value(), link.url().value(), link.sortOrder().value()))
                        .toList(),
                localization.technicalFocusAreas().stream()
                        .sorted(Comparator.comparing(area -> area.sortOrder().value()))
                        .map(area -> new PublicTechnicalFocusAreaView(
                                area.name().value(),
                                area.description() == null ? "" : area.description().value(),
                                area.sortOrder().value()))
                        .toList(),
                localization.educationSummaries().stream()
                        .sorted(Comparator.comparing(summary -> summary.sortOrder().value()))
                        .map(summary -> new PublicEducationSummaryView(
                                summary.institution().value(),
                                summary.program().value(),
                                summary.description() == null ? "" : summary.description().value(),
                                summary.sortOrder().value()))
                        .toList(),
                localization.currentFocusItems().stream()
                        .sorted(Comparator.comparing(item -> item.sortOrder().value()))
                        .map(item -> new PublicCurrentFocusItemView(item.text().value(), item.sortOrder().value()))
                        .toList());
    }
}

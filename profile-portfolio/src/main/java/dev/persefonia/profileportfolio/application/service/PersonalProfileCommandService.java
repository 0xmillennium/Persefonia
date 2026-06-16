package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.CurrentFocusItemInput;
import dev.persefonia.profileportfolio.application.command.EducationSummaryInput;
import dev.persefonia.profileportfolio.application.command.ExternalProfileLinkInput;
import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.ProfileLocalizationInput;
import dev.persefonia.profileportfolio.application.command.TechnicalFocusAreaInput;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.exception.PersonalProfileApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItem;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItemId;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.EducationDescription;
import dev.persefonia.profileportfolio.domain.profile.EducationSummary;
import dev.persefonia.profileportfolio.domain.profile.EducationSummaryId;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaDescription;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaName;
import dev.persefonia.profileportfolio.domain.profile.FocusItemText;
import dev.persefonia.profileportfolio.domain.profile.InstitutionName;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ProgramName;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusArea;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusAreaId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.List;
import java.util.Objects;

public final class PersonalProfileCommandService {
    private final PersonalProfileRepository profiles;
    private final SitePresentationSettingsRepository settings;
    private final PortfolioCommandAuthorizationPolicy authorization;

    public PersonalProfileCommandService(
            PersonalProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            PortfolioCommandAuthorizationPolicy authorization) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public PersonalProfileUpdateResult upsertActive(UpsertActivePersonalProfileCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "portfolio.profile.upsert-active");
        ContentLanguage defaultLanguage = settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new)
                .defaultLanguage();
        if (command.localizations().stream().noneMatch(input -> language(input.language()) == defaultLanguage)) {
            throw new PersonalProfileApplicationException("Default-language profile localization is required.");
        }
        try {
            List<ProfileLocalization> localizations = command.localizations().stream()
                    .map(PersonalProfileCommandService::localization)
                    .toList();
            List<ExternalProfileLink> externalLinks = command.externalLinks().stream()
                    .map(PersonalProfileCommandService::externalLink)
                    .toList();
            var current = profiles.findActiveProfile();
            boolean created = current.isEmpty();
            PersonalProfile profile = current.orElseGet(() -> PersonalProfile.create(
                    ProfileId.newId(),
                    DisplayName.of(command.displayName()),
                    true,
                    localizations,
                    externalLinks,
                    command.requestedAt()));
            if (!created) {
                profile.updateActiveProfile(
                        DisplayName.of(command.displayName()),
                        localizations,
                        externalLinks,
                        command.requestedAt());
            }
            PersonalProfile saved = profiles.save(profile);
            return new PersonalProfileUpdateResult(
                    saved.id().value(), created, saved.updatedAt(), saved.version().value());
        } catch (IllegalArgumentException | PortfolioValidationException exception) {
            throw new PersonalProfileApplicationException("Personal profile update was rejected.", exception);
        }
    }

    private static ProfileLocalization localization(ProfileLocalizationInput input) {
        return new ProfileLocalization(
                ProfileLocalizationId.newId(),
                language(input.language()),
                ShortBio.of(input.shortBio()),
                LongBio.of(input.longBio()),
                optional(input.locationText(), LocationText::of),
                input.technicalFocusAreas().stream().map(PersonalProfileCommandService::focusArea).toList(),
                input.educationSummaries().stream().map(PersonalProfileCommandService::educationSummary).toList(),
                input.currentFocusItems().stream().map(PersonalProfileCommandService::focusItem).toList());
    }

    private static TechnicalFocusArea focusArea(TechnicalFocusAreaInput input) {
        return new TechnicalFocusArea(
                TechnicalFocusAreaId.newId(),
                FocusAreaName.of(input.name()),
                optional(input.description(), FocusAreaDescription::of),
                SortOrder.of(input.sortOrder()));
    }

    private static EducationSummary educationSummary(EducationSummaryInput input) {
        return new EducationSummary(
                EducationSummaryId.newId(),
                InstitutionName.of(input.institution()),
                ProgramName.of(input.program()),
                optional(input.description(), EducationDescription::of),
                SortOrder.of(input.sortOrder()));
    }

    private static CurrentFocusItem focusItem(CurrentFocusItemInput input) {
        return new CurrentFocusItem(
                CurrentFocusItemId.newId(),
                FocusItemText.of(input.text()),
                SortOrder.of(input.sortOrder()));
    }

    private static ExternalProfileLink externalLink(ExternalProfileLinkInput input) {
        return new ExternalProfileLink(
                ExternalProfileLinkId.newId(),
                LinkLabel.of(input.label()),
                ExternalUrl.of(input.url()),
                SortOrder.of(input.sortOrder()));
    }

    private static ContentLanguage language(String value) {
        return ContentLanguage.valueOf(value);
    }

    private static <T> T optional(String value, java.util.function.Function<String, T> mapper) {
        return value == null || value.isBlank() ? null : mapper.apply(value);
    }
}

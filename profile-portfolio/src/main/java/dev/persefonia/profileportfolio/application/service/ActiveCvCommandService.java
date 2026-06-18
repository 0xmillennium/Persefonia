package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.ActiveCvCommandError;
import dev.persefonia.profileportfolio.application.command.ActiveCvSelectionInput;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ActiveCvCommandService {
    private final ActiveCvProfileRepository profiles;
    private final SitePresentationSettingsRepository settings;
    private final ActiveCvAssetEligibilityPort eligibility;
    private final PortfolioCommandAuthorizationPolicy authorization;

    public ActiveCvCommandService(
            ActiveCvProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            ActiveCvAssetEligibilityPort eligibility,
            PortfolioCommandAuthorizationPolicy authorization) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ActiveCvUpdateResult update(UpdateActiveCvCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "portfolio.active-cv.update");
        SitePresentationSettings currentSettings = settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
        ActiveCvProfile profile = profiles.findSingleton()
                .orElseThrow(() -> new IllegalStateException("Active CV profile singleton is not initialized."));

        Set<ContentLanguage> supportedLanguages = currentSettings.supportedLanguages();
        List<ActiveCvCommandError> errors = validate(command.selections(), supportedLanguages);
        if (!errors.isEmpty()) {
            return ActiveCvUpdateResult.rejected(errors);
        }

        for (ActiveCvSelectionInput selection : command.selections()) {
            ContentLanguage language = ContentLanguage.valueOf(selection.language());
            if (isBlank(selection.mediaAssetId())) {
                profile.removeDocument(language, command.requestedAt());
                continue;
            }
            MediaAssetId assetId = MediaAssetId.from(UUID.fromString(selection.mediaAssetId()));
            CvDisplayLabel displayLabel = displayLabel(selection.displayLabel());
            profile.selectDocument(language, assetId, displayLabel, command.requestedAt());
        }

        ActiveCvProfile saved = profiles.save(profile);
        return new ActiveCvUpdateResult(
                true,
                saved.id().value(),
                saved.updatedAt(),
                saved.version().value(),
                List.of());
    }

    private List<ActiveCvCommandError> validate(
            List<ActiveCvSelectionInput> selections,
            Set<ContentLanguage> supportedLanguages) {
        List<ActiveCvCommandError> errors = new ArrayList<>();
        Set<ContentLanguage> seen = new HashSet<>();
        for (int index = 0; index < selections.size(); index++) {
            ActiveCvSelectionInput selection = selections.get(index);
            String field = "selections[" + index + "]";
            ContentLanguage language = parseLanguage(selection.language(), field, errors);
            if (language == null) {
                continue;
            }
            if (!supportedLanguages.contains(language)) {
                errors.add(new ActiveCvCommandError(field + ".language", "Unsupported language."));
            }
            if (!seen.add(language)) {
                errors.add(new ActiveCvCommandError(field + ".language", "Duplicate language."));
            }
            if (isBlank(selection.mediaAssetId())) {
                continue;
            }
            MediaAssetId assetId = parseAssetId(selection.mediaAssetId(), field, errors);
            if (assetId != null && eligibility.findEligiblePublicPdf(assetId).isEmpty()) {
                errors.add(new ActiveCvCommandError(field + ".mediaAssetId", "Select a public PDF asset."));
            }
            try {
                displayLabel(selection.displayLabel());
            } catch (PortfolioValidationException exception) {
                errors.add(new ActiveCvCommandError(field + ".displayLabel", "Display label must be nonblank and at most 160 characters."));
            }
        }
        return errors.stream()
                .sorted(Comparator.comparing(ActiveCvCommandError::field)
                        .thenComparing(ActiveCvCommandError::message))
                .toList();
    }

    private static ContentLanguage parseLanguage(String value, String field, List<ActiveCvCommandError> errors) {
        if (isBlank(value)) {
            errors.add(new ActiveCvCommandError(field + ".language", "Language is required."));
            return null;
        }
        try {
            return ContentLanguage.valueOf(value);
        } catch (IllegalArgumentException exception) {
            errors.add(new ActiveCvCommandError(field + ".language", "Unsupported language."));
            return null;
        }
    }

    private static MediaAssetId parseAssetId(String value, String field, List<ActiveCvCommandError> errors) {
        try {
            return MediaAssetId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            errors.add(new ActiveCvCommandError(field + ".mediaAssetId", "Asset id must be a valid UUID."));
            return null;
        }
    }

    private static CvDisplayLabel displayLabel(String value) {
        return value == null || value.isEmpty() ? null : CvDisplayLabel.of(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

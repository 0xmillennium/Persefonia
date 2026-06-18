package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.EligibleCvAsset;
import dev.persefonia.profileportfolio.application.query.ActiveCvAdminPageData;
import dev.persefonia.profileportfolio.application.query.ActiveCvAssetCandidateView;
import dev.persefonia.profileportfolio.application.query.ActiveCvLanguageSelectionView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvDocument;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfile;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettings;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ActiveCvAdminQueryService {
    private final ActiveCvProfileRepository profiles;
    private final SitePresentationSettingsRepository settings;
    private final ActiveCvAssetEligibilityPort eligibility;

    public ActiveCvAdminQueryService(
            ActiveCvProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            ActiveCvAssetEligibilityPort eligibility) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    public ActiveCvAdminPageData pageData() {
        SitePresentationSettings currentSettings = settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new);
        ActiveCvProfile profile = profiles.findSingleton()
                .orElseThrow(() -> new IllegalStateException("Active CV profile singleton is not initialized."));
        List<ContentLanguage> supportedLanguages = currentSettings.supportedLanguages().stream()
                .sorted(Comparator.comparing(ContentLanguage::name))
                .toList();
        return new ActiveCvAdminPageData(
                supportedLanguages.stream().map(ContentLanguage::name).toList(),
                supportedLanguages.stream().map(language -> selection(profile, language)).toList(),
                eligibility.listEligiblePublicPdfCandidates().stream().map(ActiveCvAdminQueryService::candidate).toList());
    }

    private static ActiveCvLanguageSelectionView selection(ActiveCvProfile profile, ContentLanguage language) {
        return profile.documentFor(language)
                .map(document -> new ActiveCvLanguageSelectionView(
                        language.name(),
                        document.mediaAssetId().value(),
                        document.displayLabel() == null ? "" : document.displayLabel().value(),
                        document.selectedAt()))
                .orElseGet(() -> new ActiveCvLanguageSelectionView(language.name(), null, "", null));
    }

    private static ActiveCvAssetCandidateView candidate(EligibleCvAsset asset) {
        return new ActiveCvAssetCandidateView(
                asset.mediaAssetId().value(),
                asset.originalFilename(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.updatedAt());
    }
}

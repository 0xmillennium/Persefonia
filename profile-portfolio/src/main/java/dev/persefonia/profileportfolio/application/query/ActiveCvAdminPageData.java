package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record ActiveCvAdminPageData(
        List<String> supportedLanguages,
        List<ActiveCvLanguageSelectionView> selections,
        List<ActiveCvAssetCandidateView> candidates) {
    public ActiveCvAdminPageData {
        supportedLanguages = List.copyOf(supportedLanguages);
        selections = List.copyOf(selections);
        candidates = List.copyOf(candidates);
    }
}

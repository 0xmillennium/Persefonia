package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetReference;
import dev.persefonia.profileportfolio.application.query.ActiveCvPublicView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvDocument;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class ActiveCvPublicQueryService {
    private final ActiveCvProfileRepository profiles;
    private final ActiveCvPublicAssetPort assets;
    private final ActiveCvPublicLanguageResolver languages;

    public ActiveCvPublicQueryService(
            ActiveCvProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.languages = new ActiveCvPublicLanguageResolver(settings);
    }

    public Optional<ActiveCvPublicView> defaultLanguageView() {
        return languages.defaultLanguage().flatMap(this::viewFor);
    }

    public Optional<ActiveCvPublicView> explicitLanguageView(String language) {
        return languages.explicitLanguage(language).flatMap(this::viewFor);
    }

    private Optional<ActiveCvPublicView> viewFor(ContentLanguage language) {
        return profiles.findSingleton()
                .flatMap(profile -> profile.documentFor(language))
                .flatMap(document -> assets.findPublicPdf(document.mediaAssetId())
                        .filter(asset -> contentExists(document.mediaAssetId()))
                        .map(asset -> view(document, asset)));
    }

    private boolean contentExists(dev.persefonia.profileportfolio.domain.cv.MediaAssetId mediaAssetId) {
        return assets.openPublicPdf(mediaAssetId)
                .map(content -> {
                    try {
                        content.inputStream().close();
                        return true;
                    } catch (IOException exception) {
                        return false;
                    }
                })
                .orElse(false);
    }

    private static ActiveCvPublicView view(ActiveCvDocument document, ActiveCvPublicAssetReference asset) {
        ContentLanguage language = document.language();
        String languageCode = ActiveCvPublicLanguageResolver.routeCode(language);
        return new ActiveCvPublicView(
                languageCode,
                document.displayLabel() == null ? "CV" : document.displayLabel().value(),
                "/cv/" + languageCode + "/download",
                ActiveCvPublicLanguageResolver.filename(language),
                asset.contentType(),
                asset.sizeBytes(),
                document.selectedAt(),
                asset.updatedAt());
    }
}

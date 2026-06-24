package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.query.ActiveCvDownload;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Objects;
import java.util.Optional;

public final class ActiveCvPublicDownloadService {
    private final ActiveCvProfileRepository profiles;
    private final ActiveCvPublicAssetPort assets;
    private final ActiveCvPublicLanguageResolver languages;

    public ActiveCvPublicDownloadService(
            ActiveCvProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.languages = new ActiveCvPublicLanguageResolver(settings);
    }

    public Optional<ActiveCvDownload> defaultLanguageDownload() {
        return languages.defaultLanguage().flatMap(this::downloadFor);
    }

    public Optional<ActiveCvDownload> explicitLanguageDownload(String language) {
        return languages.explicitLanguage(language).flatMap(this::downloadFor);
    }

    private Optional<ActiveCvDownload> downloadFor(ContentLanguage language) {
        return profiles.findSingleton()
                .flatMap(profile -> profile.documentFor(language))
                .flatMap(document -> assets.openPublicPdf(document.mediaAssetId())
                        .map(content -> new ActiveCvDownload(
                                ActiveCvPublicLanguageResolver.routeCode(language),
                                ActiveCvPublicLanguageResolver.filename(language),
                                content.inputStream(),
                                content.contentType(),
                                content.sizeBytes(),
                                content.updatedAt())));
    }
}

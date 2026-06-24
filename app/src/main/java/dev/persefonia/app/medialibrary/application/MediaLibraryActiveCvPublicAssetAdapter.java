package dev.persefonia.app.medialibrary.application;

import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetContent;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetContentService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReference;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetContent;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetReference;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({PublicPdfAssetQueryService.class, PublicPdfAssetContentService.class})
public class MediaLibraryActiveCvPublicAssetAdapter implements ActiveCvPublicAssetPort {
    private final PublicPdfAssetQueryService queryService;
    private final PublicPdfAssetContentService contentService;

    public MediaLibraryActiveCvPublicAssetAdapter(
            PublicPdfAssetQueryService queryService,
            PublicPdfAssetContentService contentService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.contentService = Objects.requireNonNull(contentService, "contentService");
    }

    @Override
    public Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        return queryService.findEligiblePublicPdf(AssetId.from(assetId.value()))
                .map(MediaLibraryActiveCvPublicAssetAdapter::reference);
    }

    @Override
    public Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        return contentService.openPublicPdf(AssetId.from(assetId.value()))
                .map(MediaLibraryActiveCvPublicAssetAdapter::content);
    }

    private static ActiveCvPublicAssetReference reference(PublicPdfAssetReference asset) {
        return new ActiveCvPublicAssetReference(
                MediaAssetId.from(asset.assetId().value()),
                asset.contentType(),
                asset.sizeBytes(),
                asset.updatedAt());
    }

    private static ActiveCvPublicAssetContent content(PublicPdfAssetContent content) {
        return new ActiveCvPublicAssetContent(
                content.inputStream(),
                content.contentType(),
                content.contentLength(),
                content.updatedAt());
    }
}

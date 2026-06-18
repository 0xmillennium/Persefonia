package dev.persefonia.app.medialibrary.application;

import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReference;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.EligibleCvAsset;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PublicPdfAssetQueryService.class)
public class MediaLibraryActiveCvAssetEligibilityAdapter implements ActiveCvAssetEligibilityPort {
    private final PublicPdfAssetQueryService queryService;

    public MediaLibraryActiveCvAssetEligibilityAdapter(PublicPdfAssetQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    @Override
    public Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        return queryService.findEligiblePublicPdf(AssetId.from(assetId.value()))
                .map(MediaLibraryActiveCvAssetEligibilityAdapter::eligibleAsset);
    }

    @Override
    public List<EligibleCvAsset> listEligiblePublicPdfCandidates() {
        return queryService.listEligiblePublicPdfs().stream()
                .map(MediaLibraryActiveCvAssetEligibilityAdapter::eligibleAsset)
                .toList();
    }

    private static EligibleCvAsset eligibleAsset(PublicPdfAssetReference asset) {
        return new EligibleCvAsset(
                MediaAssetId.from(asset.assetId().value()),
                asset.originalFilename(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.updatedAt());
    }
}

package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;
import java.util.Optional;

public interface PublicPdfAssetReadModel {
    Optional<PublicPdfAssetReference> findEligiblePublicPdf(AssetId assetId);

    List<PublicPdfAssetReference> listEligiblePublicPdfs();
}

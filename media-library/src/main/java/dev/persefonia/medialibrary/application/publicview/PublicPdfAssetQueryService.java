package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PublicPdfAssetQueryService {
    private final PublicPdfAssetReadModel readModel;

    public PublicPdfAssetQueryService(PublicPdfAssetReadModel readModel) {
        this.readModel = Objects.requireNonNull(readModel, "readModel");
    }

    public Optional<PublicPdfAssetReference> findEligiblePublicPdf(AssetId assetId) {
        return readModel.findEligiblePublicPdf(assetId);
    }

    public List<PublicPdfAssetReference> listEligiblePublicPdfs() {
        return readModel.listEligiblePublicPdfs();
    }
}

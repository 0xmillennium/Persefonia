package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.util.List;
import java.util.Optional;

public interface ActiveCvAssetEligibilityPort {
    Optional<EligibleCvAsset> findEligiblePublicPdf(MediaAssetId assetId);

    List<EligibleCvAsset> listEligiblePublicPdfCandidates();
}

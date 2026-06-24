package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.util.Optional;

public interface ActiveCvPublicAssetPort {
    Optional<ActiveCvPublicAssetReference> findPublicPdf(MediaAssetId assetId);

    Optional<ActiveCvPublicAssetContent> openPublicPdf(MediaAssetId assetId);
}

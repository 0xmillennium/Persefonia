package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.util.Objects;
import java.util.Optional;

public final class PublicPdfAssetContentService {
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;

    public PublicPdfAssetContentService(AssetRepository assetRepository, AssetStoragePort storage) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public Optional<PublicPdfAssetContent> openPublicPdf(AssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        return assetRepository.findById(assetId)
                .filter(asset -> asset.kind() == AssetKind.PDF)
                .filter(asset -> asset.visibility() == AssetVisibility.PUBLIC)
                .filter(asset -> asset.processingStatus() == ProcessingStatus.NOT_REQUIRED)
                .filter(asset -> PDF_CONTENT_TYPE.equals(asset.contentType().value()))
                .flatMap(asset -> {
                    try {
                        return Optional.of(new PublicPdfAssetContent(
                                storage.openStored(asset.storagePath()),
                                asset.contentType().value(),
                                asset.sizeBytes().value(),
                                asset.updatedAt()));
                    } catch (RuntimeException exception) {
                        return Optional.empty();
                    }
                });
    }
}

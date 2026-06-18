package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.util.Optional;
import java.util.UUID;

public final class PublicImageVariantContentService {
    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;

    public PublicImageVariantContentService(AssetRepository assetRepository, AssetStoragePort storage) {
        this.assetRepository = java.util.Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
    }

    public Optional<PublicImageVariantContent> openVariant(String assetId, String variantName) {
        AssetId parsedAssetId;
        VariantName parsedVariantName;
        try {
            parsedAssetId = AssetId.from(UUID.fromString(assetId));
            parsedVariantName = VariantName.fromDatabaseValue(variantName);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        return assetRepository.findById(parsedAssetId)
                .filter(asset -> asset.kind() == AssetKind.IMAGE)
                .filter(asset -> asset.visibility() == AssetVisibility.PUBLIC)
                .filter(asset -> asset.processingStatus() == ProcessingStatus.PROCESSED)
                .flatMap(asset -> asset.variants().stream()
                        .filter(variant -> variant.name() == parsedVariantName)
                        .findFirst())
                .flatMap(variant -> {
                    try {
                        return Optional.of(new PublicImageVariantContent(
                                storage.openStored(variant.storagePath()),
                                variant.contentType().value(),
                                variant.sizeBytes().value()));
                    } catch (RuntimeException exception) {
                        return Optional.empty();
                    }
                });
    }
}

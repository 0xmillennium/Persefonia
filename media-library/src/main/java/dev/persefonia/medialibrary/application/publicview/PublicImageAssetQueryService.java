package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVariant;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public final class PublicImageAssetQueryService {
    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;

    public PublicImageAssetQueryService(AssetRepository assetRepository, AssetStoragePort storage) {
        this.assetRepository = java.util.Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
    }

    public Optional<PublicImageVariantView> findVariant(String assetId, String variantName) {
        return eligibleVariant(assetId, variantName).flatMap(eligible -> {
            try (InputStream ignored = storage.openStored(eligible.variant().storagePath())) {
                return Optional.of(toView(eligible.asset(), eligible.variant()));
            } catch (IOException | RuntimeException exception) {
                return Optional.empty();
            }
        });
    }

    private Optional<EligibleVariant> eligibleVariant(String assetId, String variantName) {
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
                        .findFirst()
                        .map(variant -> new EligibleVariant(asset, variant)));
    }

    private static PublicImageVariantView toView(Asset asset, AssetVariant variant) {
        return new PublicImageVariantView(
                asset.id().value().toString(),
                variant.name().databaseValue(),
                "/media/assets/" + asset.id().value() + "/variants/" + variant.name().databaseValue(),
                variant.width().value(),
                variant.height().value(),
                variant.contentType().value(),
                variant.sizeBytes().value(),
                asset.decorative().value(),
                asset.decorative().value() ? "" : asset.altText().orElseThrow().value());
    }

    private record EligibleVariant(Asset asset, AssetVariant variant) {
    }
}

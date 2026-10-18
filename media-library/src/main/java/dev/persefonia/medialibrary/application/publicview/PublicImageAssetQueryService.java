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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PublicImageAssetQueryService {
    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;

    public PublicImageAssetQueryService(AssetRepository assetRepository, AssetStoragePort storage) {
        this.assetRepository = java.util.Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
    }

    public Optional<PublicImageVariantView> findVariant(String assetId, String variantName) {
        return findVariants(assetId, Collections.singletonList(variantName)).stream().findFirst();
    }

    /** Resolve responsive candidates with one eligibility lookup; missing stored variants stay absent. */
    public List<PublicImageVariantView> findVariants(String assetId, List<String> variantNames) {
        AssetId parsedAssetId;
        Set<VariantName> requested;
        try {
            parsedAssetId = AssetId.from(UUID.fromString(assetId));
            requested = variantNames.stream().map(VariantName::fromDatabaseValue)
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            return List.of();
        }
        return assetRepository.findById(parsedAssetId)
                .filter(asset -> asset.kind() == AssetKind.IMAGE)
                .filter(asset -> asset.visibility() == AssetVisibility.PUBLIC)
                .filter(asset -> asset.processingStatus() == ProcessingStatus.PROCESSED)
                .map(asset -> asset.variants().stream()
                        .filter(variant -> requested.contains(variant.name()))
                        .flatMap(variant -> available(asset, variant).stream())
                        .sorted(Comparator.comparingInt(PublicImageVariantView::width))
                        .toList())
                .orElseGet(List::of);
    }

    private Optional<PublicImageVariantView> available(Asset asset, AssetVariant variant) {
        try (InputStream ignored = storage.openStored(variant.storagePath())) {
            return Optional.of(toView(asset, variant));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
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

}

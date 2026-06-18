package dev.persefonia.medialibrary.application.publicview;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVariant;
import dev.persefonia.medialibrary.domain.asset.AssetVariantId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.DecorativeImageFlag;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.PixelHeight;
import dev.persefonia.medialibrary.domain.asset.PixelWidth;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicImageServicesTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void publicProcessedInformativeVariantReturnsSafeViewAndContent() throws Exception {
        Asset asset = processed(AssetVisibility.PUBLIC, DecorativeImageFlag.informative(), AltText.of("Portrait"));
        FakeStorage storage = new FakeStorage();
        PublicImageAssetQueryService query = new PublicImageAssetQueryService(new FakeRepository(asset), storage);
        PublicImageVariantContentService content =
                new PublicImageVariantContentService(new FakeRepository(asset), storage);

        PublicImageVariantView view =
                query.findVariant(asset.id().value().toString(), "thumbnail").orElseThrow();
        assertThat(view.url()).isEqualTo(
                "/media/assets/" + asset.id().value() + "/variants/thumbnail");
        assertThat(view.altTextForRendering()).isEqualTo("Portrait");
        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.toString()).doesNotContain(asset.variants().getFirst().storagePath().value());

        try (InputStream input = content.openVariant(
                asset.id().value().toString(), "thumbnail").orElseThrow().inputStream()) {
            assertThat(input.readAllBytes()).isEqualTo("variant".getBytes());
        }
    }

    @Test
    void decorativeViewUsesEmptyAltText() {
        Asset asset = processed(AssetVisibility.PUBLIC, DecorativeImageFlag.decorative(), null);
        PublicImageVariantView view = new PublicImageAssetQueryService(
                new FakeRepository(asset), new FakeStorage())
                .findVariant(asset.id().value().toString(), "thumbnail")
                .orElseThrow();

        assertThat(view.decorative()).isTrue();
        assertThat(view.altTextForRendering()).isEmpty();
    }

    @Test
    void unsafeStatesInvalidNamesMissingVariantsAndMissingFilesReturnEmpty() {
        Asset privateAsset = processed(
                AssetVisibility.PRIVATE, DecorativeImageFlag.informative(), null);
        FakeStorage storage = new FakeStorage();
        PublicImageAssetQueryService privateQuery =
                new PublicImageAssetQueryService(new FakeRepository(privateAsset), storage);
        assertThat(privateQuery.findVariant(privateAsset.id().value().toString(), "thumbnail")).isEmpty();

        Asset publicAsset = processed(
                AssetVisibility.PUBLIC, DecorativeImageFlag.informative(), AltText.of("Portrait"));
        PublicImageAssetQueryService publicQuery =
                new PublicImageAssetQueryService(new FakeRepository(publicAsset), storage);
        assertThat(publicQuery.findVariant(publicAsset.id().value().toString(), "invalid")).isEmpty();
        assertThat(publicQuery.findVariant(publicAsset.id().value().toString(), "medium")).isEmpty();
        assertThat(publicQuery.findVariant("not-a-uuid", "thumbnail")).isEmpty();

        storage.missing = true;
        assertThat(publicQuery.findVariant(publicAsset.id().value().toString(), "thumbnail")).isEmpty();
        assertThat(new PublicImageVariantContentService(new FakeRepository(publicAsset), storage)
                .openVariant(publicAsset.id().value().toString(), "thumbnail")).isEmpty();
    }

    @Test
    void pendingFailedAndPdfAssetsReturnEmpty() {
        Asset pending = Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of("pending.png"), StoredFilename.of("pending.png"),
                StoragePath.of("original/pending.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(10), Checksum.of("pending"), NOW);
        Asset failed = Asset.rehydrate(
                AssetId.newId(), OriginalFilename.of("failed.png"), StoredFilename.of("failed.png"),
                StoragePath.of("original/failed.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(10), Checksum.of("failed"),
                dev.persefonia.medialibrary.domain.asset.AssetKind.IMAGE, AssetVisibility.PRIVATE,
                null, null, DecorativeImageFlag.informative(),
                dev.persefonia.medialibrary.domain.asset.ProcessingStatus.FAILED,
                List.of(), List.of(), NOW, NOW,
                dev.persefonia.medialibrary.domain.asset.Version.initial());
        Asset pdf = Asset.pdf(
                AssetId.newId(), OriginalFilename.of("cv.pdf"), StoredFilename.of("cv.pdf"),
                StoragePath.of("original/cv.pdf"), null, ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"), FileSize.of(10), Checksum.of("pdf"),
                AssetVisibility.PRIVATE, List.of(), NOW);

        for (Asset asset : List.of(pending, failed, pdf)) {
            assertThat(new PublicImageAssetQueryService(new FakeRepository(asset), new FakeStorage())
                    .findVariant(asset.id().value().toString(), "thumbnail")).isEmpty();
        }
    }

    private static Asset processed(
            AssetVisibility visibility,
            DecorativeImageFlag decorative,
            AltText altText) {
        AssetVariant variant = new AssetVariant(
                AssetVariantId.newId(), VariantName.THUMBNAIL, PixelWidth.of(320), PixelHeight.of(200),
                ContentTypeName.of("image/png"), FileSize.of(7),
                StoragePath.of("variants/asset/thumbnail-checksum.png"), null,
                Checksum.of("variant-checksum"), NOW);
        return Asset.processedImage(
                AssetId.newId(), OriginalFilename.of("portrait.png"), StoredFilename.of("portrait.png"),
                StoragePath.of("original/asset/portrait.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(100), Checksum.of("original-" + System.nanoTime()),
                visibility, ImageDimensions.of(800, 500), altText, decorative,
                List.of(variant), List.of(), NOW);
    }

    private record FakeRepository(Asset asset) implements AssetRepository {
        @Override public Asset save(Asset asset) {
            return asset;
        }
        @Override public Optional<Asset> findById(AssetId id) {
            return asset.id().equals(id) ? Optional.of(asset) : Optional.empty();
        }
        @Override public Optional<Asset> findByChecksum(Checksum checksum) {
            return Optional.empty();
        }
    }

    private static final class FakeStorage implements AssetStoragePort {
        private boolean missing;

        @Override public InputStream openStored(StoragePath storagePath) {
            if (missing) {
                throw new StorageWriteException("missing");
            }
            return new ByteArrayInputStream("variant".getBytes());
        }
        @Override public StagedAssetObject stageOriginal(OriginalAssetStagingRequest request) {
            throw new UnsupportedOperationException();
        }
        @Override public InputStream openStaged(StagedAssetObject stagedObject) {
            throw new UnsupportedOperationException();
        }
        @Override public StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey) {
            throw new UnsupportedOperationException();
        }
        @Override public StoredAssetObject storeVariant(VariantStorageRequest request) {
            throw new UnsupportedOperationException();
        }
        @Override public void deleteStagedIfExists(StagedAssetObject stagedObject) {
        }
        @Override public void deleteStoredIfExists(StoredAssetObject storedObject) {
        }
        @Override public void deleteStoredByPathIfExists(StoragePath storagePath) {
        }
    }
}

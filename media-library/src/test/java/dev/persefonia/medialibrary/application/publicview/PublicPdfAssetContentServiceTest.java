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
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.DecorativeImageFlag;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.Version;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicPdfAssetContentServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final StoragePath STORAGE_PATH = StoragePath.of("original/cv.pdf");

    @Test
    void publicPdfWithExistingOriginalContentOpensStream() throws Exception {
        Asset asset = pdf(AssetVisibility.PUBLIC, "application/pdf");
        PublicPdfAssetContentService service =
                new PublicPdfAssetContentService(new FakeRepository(asset), new FakeStorage(false));

        PublicPdfAssetContent content = service.openPublicPdf(asset.id()).orElseThrow();

        try (InputStream input = content.inputStream()) {
            assertThat(input.readAllBytes()).isEqualTo("%PDF".getBytes());
        }
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.contentLength()).isEqualTo(4);
        assertThat(content.updatedAt()).isEqualTo(NOW);
        assertThat(content.toString()).doesNotContain(STORAGE_PATH.value());
    }

    @Test
    void privatePdfReturnsEmpty() {
        Asset asset = pdf(AssetVisibility.PRIVATE, "application/pdf");

        assertThat(service(asset).openPublicPdf(asset.id())).isEmpty();
    }

    @Test
    void publicImageReturnsEmpty() {
        Asset asset = Asset.rehydrate(
                AssetId.newId(),
                OriginalFilename.of("image.png"),
                StoredFilename.of("image.png"),
                StoragePath.of("original/image.png"),
                null,
                ContentTypeName.of("image/png"),
                FileExtension.of("png"),
                FileSize.of(4),
                Checksum.of("image"),
                AssetKind.IMAGE,
                AssetVisibility.PUBLIC,
                dev.persefonia.medialibrary.domain.asset.ImageDimensions.of(1, 1),
                dev.persefonia.medialibrary.domain.asset.AltText.of("Image"),
                DecorativeImageFlag.informative(),
                ProcessingStatus.PROCESSED,
                List.of(),
                List.of(),
                NOW,
                NOW,
                Version.initial());

        assertThat(service(asset).openPublicPdf(asset.id())).isEmpty();
    }

    @Test
    void missingAssetReturnsEmpty() {
        Asset asset = pdf(AssetVisibility.PUBLIC, "application/pdf");

        assertThat(service(asset).openPublicPdf(AssetId.newId())).isEmpty();
    }

    @Test
    void missingStoredFileReturnsEmpty() {
        Asset asset = pdf(AssetVisibility.PUBLIC, "application/pdf");
        PublicPdfAssetContentService service =
                new PublicPdfAssetContentService(new FakeRepository(asset), new FakeStorage(true));

        assertThat(service.openPublicPdf(asset.id())).isEmpty();
    }

    @Test
    void wrongContentTypeReturnsEmpty() {
        Asset asset = pdf(AssetVisibility.PUBLIC, "application/x-pdf");

        assertThat(service(asset).openPublicPdf(asset.id())).isEmpty();
    }

    private static PublicPdfAssetContentService service(Asset asset) {
        return new PublicPdfAssetContentService(new FakeRepository(asset), new FakeStorage(false));
    }

    private static Asset pdf(AssetVisibility visibility, String contentType) {
        return Asset.pdf(
                AssetId.newId(),
                OriginalFilename.of("cv.pdf"),
                StoredFilename.of("cv.pdf"),
                STORAGE_PATH,
                null,
                ContentTypeName.of(contentType),
                FileExtension.of("pdf"),
                FileSize.of(4),
                Checksum.of("pdf-" + contentType + "-" + visibility),
                visibility,
                List.of(),
                NOW);
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

    private record FakeStorage(boolean missing) implements AssetStoragePort {
        @Override public InputStream openStored(StoragePath storagePath) {
            if (missing) {
                throw new StorageWriteException("missing");
            }
            return new ByteArrayInputStream("%PDF".getBytes());
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

package dev.persefonia.app.medialibrary.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetContentService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReference;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaLibraryActiveCvPublicAssetAdapterTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final AssetId ASSET_ID = AssetId.from(UUID.fromString("00000000-0000-0000-0000-00000000a861"));
    private static final MediaAssetId MEDIA_ASSET_ID = MediaAssetId.from(ASSET_ID.value());
    private static final StoragePath STORAGE_PATH = StoragePath.of("original/cv.pdf");

    @Test
    void bridgesPublicPdfReference() {
        MediaLibraryActiveCvPublicAssetAdapter adapter = adapter(pdf(AssetVisibility.PUBLIC), false);

        var reference = adapter.findPublicPdf(MEDIA_ASSET_ID).orElseThrow();

        assertThat(reference.mediaAssetId()).isEqualTo(MEDIA_ASSET_ID);
        assertThat(reference.contentType()).isEqualTo("application/pdf");
        assertThat(reference.sizeBytes()).isEqualTo(4);
        assertThat(reference.updatedAt()).isEqualTo(NOW);
        assertThat(reference.toString()).doesNotContain(STORAGE_PATH.value());
    }

    @Test
    void bridgesPublicPdfContent() throws Exception {
        MediaLibraryActiveCvPublicAssetAdapter adapter = adapter(pdf(AssetVisibility.PUBLIC), false);

        var content = adapter.openPublicPdf(MEDIA_ASSET_ID).orElseThrow();

        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.sizeBytes()).isEqualTo(4);
        assertThat(content.inputStream().readAllBytes()).isEqualTo("%PDF".getBytes());
        assertThat(content.toString()).doesNotContain(STORAGE_PATH.value());
    }

    @Test
    void ineligibleAssetReturnsEmpty() {
        MediaLibraryActiveCvPublicAssetAdapter adapter = adapter(pdf(AssetVisibility.PRIVATE), false);

        assertThat(adapter.findPublicPdf(MEDIA_ASSET_ID)).isEmpty();
        assertThat(adapter.openPublicPdf(MEDIA_ASSET_ID)).isEmpty();
    }

    @Test
    void missingContentReturnsEmpty() {
        MediaLibraryActiveCvPublicAssetAdapter adapter = adapter(pdf(AssetVisibility.PUBLIC), true);

        assertThat(adapter.findPublicPdf(MEDIA_ASSET_ID)).isPresent();
        assertThat(adapter.openPublicPdf(MEDIA_ASSET_ID)).isEmpty();
    }

    private static MediaLibraryActiveCvPublicAssetAdapter adapter(Asset asset, boolean missingContent) {
        FakeRepository repository = new FakeRepository(asset);
        PublicPdfAssetQueryService queryService = new PublicPdfAssetQueryService(new FakeReadModel(asset));
        PublicPdfAssetContentService contentService =
                new PublicPdfAssetContentService(repository, new FakeStorage(missingContent));
        return new MediaLibraryActiveCvPublicAssetAdapter(queryService, contentService);
    }

    private static Asset pdf(AssetVisibility visibility) {
        return Asset.pdf(
                ASSET_ID,
                OriginalFilename.of("cv.pdf"),
                StoredFilename.of("cv.pdf"),
                STORAGE_PATH,
                null,
                ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"),
                FileSize.of(4),
                Checksum.of("bridge-pdf-" + visibility),
                visibility,
                List.of(),
                NOW);
    }

    private record FakeReadModel(Asset asset) implements PublicPdfAssetReadModel {
        @Override
        public Optional<PublicPdfAssetReference> findEligiblePublicPdf(AssetId assetId) {
            if (!asset.id().equals(assetId) || asset.visibility() != AssetVisibility.PUBLIC) {
                return Optional.empty();
            }
            return Optional.of(new PublicPdfAssetReference(
                    asset.id(),
                    asset.originalFilename().value(),
                    asset.contentType().value(),
                    asset.sizeBytes().value(),
                    asset.updatedAt()));
        }

        @Override
        public List<PublicPdfAssetReference> listEligiblePublicPdfs() {
            return findEligiblePublicPdf(asset.id()).stream().toList();
        }
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

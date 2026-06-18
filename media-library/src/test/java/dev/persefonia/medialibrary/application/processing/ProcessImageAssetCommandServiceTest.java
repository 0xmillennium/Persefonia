package dev.persefonia.medialibrary.application.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.PixelHeight;
import dev.persefonia.medialibrary.domain.asset.PixelWidth;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcessImageAssetCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void processesPendingImageWithDimensionsVariantsAndValidationWithoutMakingItPublic() {
        Fixture fixture = new Fixture(pendingImage("image/jpeg", "jpg"));

        ProcessImageAssetResult result = fixture.service.process(
                new ProcessImageAssetCommand(fixture.repository.asset.id()));

        assertThat(result).isInstanceOf(ProcessImageAssetResult.Processed.class);
        Asset saved = fixture.repository.asset;
        assertThat(saved.processingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(saved.visibility()).isEqualTo(AssetVisibility.PRIVATE);
        assertThat(saved.imageDimensions()).contains(ImageDimensions.of(1200, 800));
        assertThat(saved.variants()).extracting(variant -> variant.name().databaseValue())
                .containsExactly("thumbnail", "medium", "large", "og");
        assertThat(saved.variants()).allSatisfy(variant -> {
            assertThat(variant.storagePath().value())
                    .startsWith("variants/" + saved.id().value() + "/" + variant.name().databaseValue() + "-");
            assertThat(variant.publicUrlOptional()).isEmpty();
            assertThat(variant.sizeBytes().value()).isPositive();
            assertThat(variant.checksum().value()).isNotBlank();
        });
        assertThat(saved.validationResults()).extracting(resultValue -> resultValue.rule().value())
                .contains("image_decode", "image_dimensions_read", "image_variants_generated");
    }

    @Test
    void processesPendingPngIntoPngVariants() {
        Fixture fixture = new Fixture(pendingImage("image/png", "png"));

        fixture.service.process(new ProcessImageAssetCommand(fixture.repository.asset.id()));

        assertThat(fixture.repository.asset.processingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(fixture.repository.asset.variants()).allSatisfy(variant -> {
            assertThat(variant.contentType().value()).isEqualTo("image/png");
            assertThat(variant.storagePath().value()).endsWith(".png");
        });
    }

    @Test
    void returnsAlreadyProcessedNotProcessableAndNotFound() {
        Asset processed = pendingImage("image/png", "png");
        processed.markProcessed(ImageDimensions.of(10, 10), NOW.plusSeconds(1));
        Fixture processedFixture = new Fixture(processed);
        assertThat(processedFixture.service.process(new ProcessImageAssetCommand(processed.id())))
                .isInstanceOf(ProcessImageAssetResult.AlreadyProcessed.class);

        Asset pdf = Asset.pdf(
                AssetId.newId(), OriginalFilename.of("cv.pdf"), StoredFilename.of("cv.pdf"),
                StoragePath.of("original/pdf/cv.pdf"), null, ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"), FileSize.of(5), Checksum.of("pdf"),
                AssetVisibility.PRIVATE, List.of(), NOW);
        Fixture pdfFixture = new Fixture(pdf);
        assertThat(pdfFixture.service.process(new ProcessImageAssetCommand(pdf.id())))
                .isInstanceOf(ProcessImageAssetResult.NotProcessable.class);

        Fixture missing = new Fixture(null);
        assertThat(missing.service.process(new ProcessImageAssetCommand(AssetId.newId())))
                .isInstanceOf(ProcessImageAssetResult.NotFound.class);
    }

    @Test
    void invalidImageMarksFailedWithoutDimensions() {
        Fixture fixture = new Fixture(pendingImage("image/png", "png"));
        fixture.metadataReader.fail = true;

        ProcessImageAssetResult result = fixture.service.process(
                new ProcessImageAssetCommand(fixture.repository.asset.id()));

        assertThat(result).isInstanceOf(ProcessImageAssetResult.Failed.class);
        assertThat(fixture.repository.asset.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(fixture.repository.asset.visibility()).isEqualTo(AssetVisibility.PRIVATE);
        assertThat(fixture.repository.asset.imageDimensions()).isEmpty();
        assertThat(fixture.repository.asset.validationResults())
                .anySatisfy(validation -> {
                    assertThat(validation.rule().value()).isEqualTo("image_processing");
                    assertThat(validation.status().name()).isEqualTo("FAILED");
                });
    }

    @Test
    void storageFailureMarksFailedAndCleansAlreadyStoredVariants() {
        Fixture fixture = new Fixture(pendingImage("image/png", "png"));
        fixture.storage.failStoreAt = 3;

        ProcessImageAssetResult result = fixture.service.process(
                new ProcessImageAssetCommand(fixture.repository.asset.id()));

        assertThat(result).isInstanceOf(ProcessImageAssetResult.Failed.class);
        assertThat(fixture.repository.asset.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(fixture.storage.deletedPaths).hasSize(2);
        assertThat(fixture.repository.asset.variants()).isEmpty();
    }

    @Test
    void repositoryFailureAfterVariantWritesCleansVariantsAndPropagates() {
        Fixture fixture = new Fixture(pendingImage("image/jpeg", "jpg"));
        fixture.repository.failSave = true;

        assertThatThrownBy(() -> fixture.service.process(
                new ProcessImageAssetCommand(fixture.repository.asset.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
        assertThat(fixture.storage.deletedPaths).hasSize(4);
    }

    private static Asset pendingImage(String contentType, String extension) {
        return Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of("image." + extension),
                StoredFilename.of("checksum." + extension),
                StoragePath.of("original/asset/checksum." + extension), null,
                ContentTypeName.of(contentType), FileExtension.of(extension), FileSize.of(100),
                Checksum.of("original-" + extension), NOW);
    }

    private static final class Fixture {
        private final FakeRepository repository;
        private final FakeStorage storage = new FakeStorage();
        private final FakeMetadataReader metadataReader = new FakeMetadataReader();
        private final ProcessImageAssetCommandService service;

        private Fixture(Asset asset) {
            repository = new FakeRepository(asset);
            service = new ProcessImageAssetCommandService(
                    repository,
                    storage,
                    metadataReader,
                    request -> ImageVariantSpecs.all().stream()
                            .map(spec -> new GeneratedImageVariant(
                                    spec.name(),
                                    PixelWidth.of(Math.min(100, spec.maximumWidth())),
                                    PixelHeight.of(Math.min(50, spec.maximumHeight())),
                                    request.originalContentType(),
                                    FileExtension.of(request.originalContentType().value().equals("image/png")
                                            ? "png" : "jpg"),
                                    ("variant-" + spec.name().databaseValue()).getBytes()))
                            .toList(),
                    new ChecksumCalculator(),
                    Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        }
    }

    private static final class FakeMetadataReader implements ImageMetadataReader {
        private boolean fail;

        @Override
        public ImageMetadata read(byte[] imageBytes) {
            if (fail) {
                throw new ImageProcessingException("Image bytes could not be decoded.");
            }
            return new ImageMetadata(ImageDimensions.of(1200, 800));
        }
    }

    private static final class FakeRepository implements AssetRepository {
        private Asset asset;
        private boolean failSave;

        private FakeRepository(Asset asset) {
            this.asset = asset;
        }

        @Override
        public Asset save(Asset asset) {
            if (failSave) {
                throw new IllegalStateException("save failed");
            }
            this.asset = asset;
            return asset;
        }

        @Override
        public Optional<Asset> findById(AssetId id) {
            return asset != null && asset.id().equals(id) ? Optional.of(asset) : Optional.empty();
        }

        @Override
        public Optional<Asset> findByChecksum(Checksum checksum) {
            return Optional.empty();
        }
    }

    private static final class FakeStorage implements AssetStoragePort {
        private final List<StoragePath> deletedPaths = new ArrayList<>();
        private int storeCount;
        private int failStoreAt = Integer.MAX_VALUE;

        @Override
        public InputStream openStored(StoragePath storagePath) {
            return new ByteArrayInputStream("original".getBytes());
        }

        @Override
        public StoredAssetObject storeVariant(VariantStorageRequest request) {
            storeCount++;
            if (storeCount == failStoreAt) {
                throw new StorageWriteException("store failed");
            }
            return new StoredAssetObject(request.storagePath().value());
        }

        @Override
        public void deleteStoredByPathIfExists(StoragePath storagePath) {
            deletedPaths.add(storagePath);
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
        @Override public void deleteStagedIfExists(StagedAssetObject stagedObject) {
        }
        @Override public void deleteStoredIfExists(StoredAssetObject storedObject) {
        }
    }
}

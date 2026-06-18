package dev.persefonia.medialibrary.application.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UploadAssetCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0x01);
    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01);
    private static final byte[] PDF = "%PDF-1.7".getBytes();

    private final FakeAssetRepository repository = new FakeAssetRepository();
    private final FakeStorage storage = new FakeStorage();
    private final UploadAssetCommandService service = new UploadAssetCommandService(
            repository,
            storage,
            new UploadValidationPolicy(32, 32),
            new MediaContentSniffer(),
            new ChecksumCalculator(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void uploadsValidJpegAndPngAsPrivatePendingImages() {
        for (UploadAssetCommand command : List.of(
                command("portrait.jpeg", "image/jpeg", "jpeg", JPEG, JPEG.length),
                command("portrait.png", "image/png", "png", PNG, PNG.length))) {
            UploadAssetResult result = service.upload(command);
            Asset asset = repository.saved.getLast();

            assertThat(result).isInstanceOf(UploadAssetResult.Created.class);
            assertThat(asset.kind()).isEqualTo(AssetKind.IMAGE);
            assertThat(asset.visibility()).isEqualTo(AssetVisibility.PRIVATE);
            assertThat(asset.processingStatus()).isEqualTo(ProcessingStatus.PENDING);
            assertThat(asset.imageDimensions()).isEmpty();
            assertThat(asset.variants()).isEmpty();
            assertThat(asset.publicUrl()).isEmpty();
            assertThat(asset.checksum().value()).hasSize(64);
            assertThat(asset.validationResults()).hasSize(5);
        }
    }

    @Test
    void uploadsPdfAsPrivateNotRequiredAssetAndUsesCanonicalExtensions() {
        service.upload(command("resume.PDF", "application/pdf", ".PDF", PDF, PDF.length));
        Asset pdf = repository.saved.getLast();

        assertThat(pdf.kind()).isEqualTo(AssetKind.PDF);
        assertThat(pdf.visibility()).isEqualTo(AssetVisibility.PRIVATE);
        assertThat(pdf.processingStatus()).isEqualTo(ProcessingStatus.NOT_REQUIRED);
        assertThat(pdf.fileExtension().value()).isEqualTo("pdf");
        assertThat(pdf.storedFilename().value()).endsWith(".pdf");
        assertThat(pdf.storagePath().value())
                .isEqualTo("original/" + pdf.id().value() + "/" + pdf.storedFilename().value());
        assertThat(pdf.publicUrl()).isEmpty();
    }

    @Test
    void jpegUsesCanonicalJpgExtension() {
        service.upload(command("photo.jpeg", "image/jpeg", "jpeg", JPEG, JPEG.length));

        assertThat(repository.saved.getLast().fileExtension().value()).isEqualTo("jpg");
        assertThat(repository.saved.getLast().storedFilename().value()).endsWith(".jpg");
    }

    @Test
    void rejectsUnsupportedTypesWithoutStagingOrPersistence() {
        for (String[] type : List.of(
                new String[] {"image/svg+xml", "svg"},
                new String[] {"image/webp", "webp"},
                new String[] {"image/gif", "gif"},
                new String[] {
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
                })) {
            UploadAssetResult result =
                    service.upload(command("bad." + type[1], type[0], type[1], "bad".getBytes(), 3));
            assertThat(result).isInstanceOf(UploadAssetResult.Rejected.class);
        }

        assertThat(repository.saved).isEmpty();
        assertThat(storage.stageCount).isZero();
    }

    @Test
    void rejectsUnknownMagicMismatchEmptyOversizeAndDeclaredSizeMismatch() {
        assertRejected(command("unknown.jpg", "image/jpeg", "jpg", "what".getBytes(), 4));
        assertRejected(command("empty.png", "image/png", "png", new byte[0], 1));
        assertRejected(command("large.jpg", "image/jpeg", "jpg", JPEG, 33));
        assertRejected(command("wrong.png", "image/png", "png", PNG, PNG.length + 1));
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void rejectsContentTypeExtensionMismatchWithoutStaging() {
        UploadAssetResult result =
                service.upload(command("wrong.png", "image/jpeg", "png", JPEG, JPEG.length));

        assertThat(result).isInstanceOf(UploadAssetResult.Rejected.class);
        assertThat(storage.stageCount).isZero();
    }

    @Test
    void duplicateChecksumReturnsExistingIdAndDeletesStagedObject() throws Exception {
        String checksum = new ChecksumCalculator().calculate(new ByteArrayInputStream(PNG));
        Asset existing = validExistingAsset(Checksum.of(checksum));
        repository.duplicate = existing;

        UploadAssetResult result =
                service.upload(command("copy.png", "image/png", "png", PNG, PNG.length));

        assertThat(result).isEqualTo(new UploadAssetResult.Duplicate(existing.id()));
        assertThat(repository.saved).isEmpty();
        assertThat(storage.stagedDeleted).isTrue();
    }

    @Test
    void repositoryFailureDeletesCommittedFileAndRethrows() {
        repository.failSave = true;

        assertThatThrownBy(() -> service.upload(
                command("portrait.png", "image/png", "png", PNG, PNG.length)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.storedDeleted).isTrue();
    }

    @Test
    void storageWriteFailureThrowsInfrastructureExceptionWithoutPersistence() {
        storage.failStage = true;

        assertThatThrownBy(() -> service.upload(
                command("portrait.png", "image/png", "png", PNG, PNG.length)))
                .isInstanceOf(UploadAssetException.class);
        assertThat(repository.saved).isEmpty();
    }

    private void assertRejected(UploadAssetCommand command) {
        assertThat(service.upload(command)).isInstanceOf(UploadAssetResult.Rejected.class);
    }

    private static UploadAssetCommand command(
            String filename,
            String contentType,
            String extension,
            byte[] content,
            long declaredSize) {
        return new UploadAssetCommand(
                filename,
                contentType,
                extension,
                declaredSize,
                () -> new ByteArrayInputStream(content));
    }

    private static Asset validExistingAsset(Checksum checksum) {
        return Asset.pendingImage(
                AssetId.newId(),
                dev.persefonia.medialibrary.domain.asset.OriginalFilename.of("existing.png"),
                dev.persefonia.medialibrary.domain.asset.StoredFilename.of(checksum.value() + ".png"),
                dev.persefonia.medialibrary.domain.asset.StoragePath.of("original/existing/" + checksum.value() + ".png"),
                null,
                dev.persefonia.medialibrary.domain.asset.ContentTypeName.of("image/png"),
                dev.persefonia.medialibrary.domain.asset.FileExtension.of("png"),
                dev.persefonia.medialibrary.domain.asset.FileSize.of(PNG.length),
                checksum,
                NOW);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private static final class FakeAssetRepository implements AssetRepository {
        private final List<Asset> saved = new ArrayList<>();
        private Asset duplicate;
        private boolean failSave;

        @Override
        public Asset save(Asset asset) {
            if (failSave) {
                throw new IllegalStateException("save failed");
            }
            saved.add(asset);
            return asset;
        }

        @Override
        public Optional<Asset> findById(AssetId id) {
            return saved.stream().filter(asset -> asset.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Asset> findByChecksum(Checksum checksum) {
            return duplicate != null && duplicate.checksum().equals(checksum)
                    ? Optional.of(duplicate)
                    : Optional.empty();
        }
    }

    private static final class FakeStorage implements AssetStoragePort {
        private byte[] staged;
        private int stageCount;
        private boolean stagedDeleted;
        private boolean storedDeleted;
        private boolean failStage;

        @Override
        public StagedAssetObject stageOriginal(OriginalAssetStagingRequest request) {
            if (failStage) {
                throw new StorageWriteException("stage failed");
            }
            stageCount++;
            try (InputStream input = request.byteSource().openStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                byte[] all = output.toByteArray();
                int length = (int) Math.min(all.length, request.maximumBytesToStage());
                staged = java.util.Arrays.copyOf(all, length);
                return new StagedAssetObject("staged", staged.length);
            } catch (IOException exception) {
                throw new StorageWriteException("stage failed", exception);
            }
        }

        @Override
        public InputStream openStaged(StagedAssetObject stagedObject) {
            return new ByteArrayInputStream(staged);
        }

        @Override
        public StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey) {
            return new StoredAssetObject(finalKey.value());
        }

        @Override
        public void deleteStagedIfExists(StagedAssetObject stagedObject) {
            stagedDeleted = true;
            staged = null;
        }

        @Override
        public void deleteStoredIfExists(StoredAssetObject storedObject) {
            storedDeleted = true;
        }
    }
}

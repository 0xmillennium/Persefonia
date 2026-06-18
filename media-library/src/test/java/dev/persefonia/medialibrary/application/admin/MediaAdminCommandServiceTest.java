package dev.persefonia.medialibrary.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.processing.GeneratedImageVariant;
import dev.persefonia.medialibrary.application.processing.ImageMetadata;
import dev.persefonia.medialibrary.application.processing.ImageMetadataReader;
import dev.persefonia.medialibrary.application.processing.ImageProcessingException;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerationRequest;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerator;
import dev.persefonia.medialibrary.application.processing.ImageVariantSpecs;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.application.upload.MediaContentSniffer;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaAdminCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaCommandActor OWNER =
            new MediaCommandActor(UUID.fromString("00000000-0000-0000-0000-000000000001"), true, true);
    private static final MediaCommandActor EDITOR =
            new MediaCommandActor(UUID.fromString("00000000-0000-0000-0000-000000000002"), true, false);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0x01);
    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01);
    private static final byte[] PDF = "%PDF-1.7".getBytes();

    @Test
    void ownerUploadsImagesAndProcessingCreatesPrivateProcessedVariants() {
        for (AdminUploadAssetCommand command : List.of(
                command("photo.jpg", "image/jpeg", "jpg", JPEG),
                command("diagram.png", "image/png", "png", PNG))) {
            Fixture fixture = new Fixture();

            AdminUploadAssetResult result = fixture.service.upload(command);

            assertThat(result).isInstanceOf(AdminUploadAssetResult.Created.class);
            Asset asset = fixture.repository.onlyAsset();
            assertThat(asset.kind()).isEqualTo(AssetKind.IMAGE);
            assertThat(asset.visibility()).isEqualTo(AssetVisibility.PRIVATE);
            assertThat(asset.processingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
            assertThat(asset.variants()).hasSize(4);
            assertThat(fixture.storage.storedVariants).hasSize(4);
        }
    }

    @Test
    void ownerUploadsPdfAsPrivateNotRequiredWithoutImageProcessing() {
        Fixture fixture = new Fixture();

        fixture.service.upload(command("cv.pdf", "application/pdf", "pdf", PDF));

        Asset asset = fixture.repository.onlyAsset();
        assertThat(asset.kind()).isEqualTo(AssetKind.PDF);
        assertThat(asset.visibility()).isEqualTo(AssetVisibility.PRIVATE);
        assertThat(asset.processingStatus()).isEqualTo(ProcessingStatus.NOT_REQUIRED);
        assertThat(asset.variants()).isEmpty();
        assertThat(fixture.storage.storedVariants).isEmpty();
    }

    @Test
    void uploadRejectionAndDuplicateDoNotCreateNewAssets() throws Exception {
        Fixture rejected = new Fixture();
        assertThat(rejected.service.upload(command("bad.svg", "image/svg+xml", "svg", "bad".getBytes())))
                .isInstanceOf(AdminUploadAssetResult.Rejected.class);
        assertThat(rejected.repository.assets).isEmpty();

        Fixture duplicate = new Fixture();
        Asset existing = pdf("existing", Checksum.of(new ChecksumCalculator()
                .calculate(new ByteArrayInputStream(PDF))));
        duplicate.repository.save(existing);

        assertThat(duplicate.service.upload(command("copy.pdf", "application/pdf", "pdf", PDF)))
                .isEqualTo(new AdminUploadAssetResult.Duplicate(existing.id()));
        assertThat(duplicate.repository.assets).hasSize(1);
    }

    @Test
    void processingFailureReturnsCreatedWithFailedStatus() {
        Fixture fixture = new Fixture();
        fixture.metadataReader.fail = true;

        AdminUploadAssetResult result = fixture.service.upload(command("broken.png", "image/png", "png", PNG));

        assertThat(result)
                .isInstanceOfSatisfying(AdminUploadAssetResult.Created.class, created -> {
                    assertThat(created.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
                    assertThat(created.warningOptional()).contains("Image bytes could not be decoded.");
                });
        assertThat(fixture.repository.onlyAsset().processingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    void nonOwnerCannotUploadOrUpdateMetadata() {
        Fixture fixture = new Fixture();
        fixture.authorization.requireOwner = true;

        assertThatThrownBy(() -> fixture.service.upload(command(EDITOR, "photo.jpg", "image/jpeg", "jpg", JPEG)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                EDITOR, AssetId.newId(), AssetVisibility.PUBLIC, "Alt", false)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void metadataUpdateEnforcesImageAndPdfRulesWithoutTouchingStorage() {
        Fixture fixture = new Fixture();
        Asset image = fixture.uploadedProcessedPng();

        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, image.id(), AssetVisibility.PUBLIC, "Useful chart", false)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(image.id()));
        assertThat(fixture.repository.findById(image.id()).orElseThrow().visibility()).isEqualTo(AssetVisibility.PUBLIC);

        Asset decorative = fixture.uploadedProcessedPng();
        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, decorative.id(), AssetVisibility.PUBLIC, null, true)))
                .isInstanceOf(AssetMetadataUpdateResult.Updated.class);

        Asset pending = Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of("pending.png"), StoredFilename.of("pending.png"),
                StoragePath.of("original/pending.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(10), Checksum.of("pending"), NOW);
        fixture.repository.save(pending);

        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, pending.id(), AssetVisibility.PUBLIC, "Alt", false)))
                .isInstanceOf(AssetMetadataUpdateResult.Rejected.class);

        Asset pdf = pdf("pdf", Checksum.of("pdf"));
        fixture.repository.save(pdf);
        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, pdf.id(), AssetVisibility.PUBLIC, null, false)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(pdf.id()));
        assertThat(fixture.storage.storedVariants).hasSize(8);
    }

    @Test
    void publicProcessedImageWithoutAltOrDecorativeAndMissingAssetAreRejected() {
        Fixture fixture = new Fixture();
        Asset image = fixture.uploadedProcessedPng();

        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, image.id(), AssetVisibility.PUBLIC, null, false)))
                .isInstanceOf(AssetMetadataUpdateResult.Rejected.class);
        assertThat(fixture.service.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, AssetId.newId(), AssetVisibility.PUBLIC, "Alt", false)))
                .isInstanceOf(AssetMetadataUpdateResult.NotFound.class);
    }

    private static AdminUploadAssetCommand command(String filename, String contentType, String extension, byte[] bytes) {
        return command(OWNER, filename, contentType, extension, bytes);
    }

    private static AdminUploadAssetCommand command(
            MediaCommandActor actor, String filename, String contentType, String extension, byte[] bytes) {
        return new AdminUploadAssetCommand(
                actor, filename, contentType, extension, bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    private static Asset pdf(String key, Checksum checksum) {
        return Asset.pdf(
                AssetId.newId(), OriginalFilename.of(key + ".pdf"), StoredFilename.of(key + ".pdf"),
                StoragePath.of("original/" + key + ".pdf"), null, ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"), FileSize.of(PDF.length), checksum, AssetVisibility.PRIVATE, List.of(), NOW);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private static final class Fixture {
        private final FakeRepository repository = new FakeRepository();
        private final FakeStorage storage = new FakeStorage();
        private final FakeAuthorization authorization = new FakeAuthorization();
        private final FakeMetadataReader metadataReader = new FakeMetadataReader();
        private final MediaAdminCommandService service;

        private Fixture() {
            ChecksumCalculator checksums = new ChecksumCalculator();
            service = new MediaAdminCommandService(
                    authorization,
                    new UploadAssetCommandService(
                            repository,
                            storage,
                            new UploadValidationPolicy(128, 128),
                            new MediaContentSniffer(),
                            checksums,
                            Clock.fixed(NOW, ZoneOffset.UTC)),
                    new ProcessImageAssetCommandService(
                            repository,
                            storage,
                            metadataReader,
                            new FakeVariantGenerator(),
                            checksums,
                            Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)),
                    repository,
                    Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
        }

        private Asset uploadedProcessedPng() {
            byte[] bytes = java.util.Arrays.copyOf(PNG, PNG.length + 1);
            bytes[bytes.length - 1] = (byte) repository.assets.size();
            AdminUploadAssetResult.Created created = (AdminUploadAssetResult.Created)
                    service.upload(command("image-" + repository.assets.size() + ".png", "image/png", "png", bytes));
            return repository.findById(created.assetId()).orElseThrow();
        }
    }

    private static final class FakeAuthorization implements MediaCommandAuthorizationPolicy {
        private boolean requireOwner;

        @Override
        public void requireOwner(MediaCommandActor actor, String commandName) {
            if (requireOwner && !actor.owner()) {
                throw new SecurityException(commandName);
            }
        }
    }

    private static final class FakeRepository implements AssetRepository {
        private final Map<AssetId, Asset> assets = new LinkedHashMap<>();

        @Override
        public Asset save(Asset asset) {
            assets.put(asset.id(), asset);
            return asset;
        }

        @Override
        public Optional<Asset> findById(AssetId id) {
            return Optional.ofNullable(assets.get(id));
        }

        @Override
        public Optional<Asset> findByChecksum(Checksum checksum) {
            return assets.values().stream().filter(asset -> asset.checksum().equals(checksum)).findFirst();
        }

        private Asset onlyAsset() {
            assertThat(assets).hasSize(1);
            return assets.values().iterator().next();
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

    private static final class FakeVariantGenerator implements ImageVariantGenerator {
        @Override
        public List<GeneratedImageVariant> generate(ImageVariantGenerationRequest request) {
            return ImageVariantSpecs.all().stream()
                    .map(spec -> new GeneratedImageVariant(
                            spec.name(),
                            PixelWidth.of(Math.min(100, spec.maximumWidth())),
                            PixelHeight.of(Math.min(50, spec.maximumHeight())),
                            request.originalContentType(),
                            FileExtension.of(request.originalContentType().value().equals("image/png") ? "png" : "jpg"),
                            ("variant-" + spec.name().databaseValue()).getBytes()))
                    .toList();
        }
    }

    private static final class FakeStorage implements AssetStoragePort {
        private final Map<String, byte[]> staged = new LinkedHashMap<>();
        private final Map<String, byte[]> stored = new LinkedHashMap<>();
        private final Map<String, byte[]> storedVariants = new LinkedHashMap<>();

        @Override
        public StagedAssetObject stageOriginal(OriginalAssetStagingRequest request) {
            try (InputStream input = request.byteSource().openStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                byte[] bytes = output.toByteArray();
                String key = "staged-" + staged.size();
                staged.put(key, bytes);
                return new StagedAssetObject(key, bytes.length);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream openStaged(StagedAssetObject stagedObject) {
            return new ByteArrayInputStream(staged.get(stagedObject.stagingKey()));
        }

        @Override
        public StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey) {
            stored.put(finalKey.value(), staged.remove(stagedObject.stagingKey()));
            return new StoredAssetObject(finalKey.value());
        }

        @Override
        public InputStream openStored(StoragePath storagePath) {
            return new ByteArrayInputStream(stored.get(storagePath.value()));
        }

        @Override
        public StoredAssetObject storeVariant(VariantStorageRequest request) {
            storedVariants.put(request.storagePath().value(), request.content());
            return new StoredAssetObject(request.storagePath().value());
        }

        @Override public void deleteStagedIfExists(StagedAssetObject stagedObject) {
            staged.remove(stagedObject.stagingKey());
        }

        @Override public void deleteStoredIfExists(StoredAssetObject storedObject) {
            stored.remove(storedObject.logicalPath());
        }

        @Override public void deleteStoredByPathIfExists(StoragePath storagePath) {
            storedVariants.remove(storagePath.value());
        }
    }
}

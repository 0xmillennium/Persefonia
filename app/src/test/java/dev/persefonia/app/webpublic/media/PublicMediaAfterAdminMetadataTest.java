package dev.persefonia.app.webpublic.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.processing.ImageMetadata;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerationRequest;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
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
import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResult;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResultId;
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
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.ValidationMessage;
import dev.persefonia.medialibrary.domain.asset.ValidationRuleName;
import dev.persefonia.medialibrary.domain.asset.ValidationStatus;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import dev.persefonia.webpublic.media.PublicMediaAssetController;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicMediaAfterAdminMetadataTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");
    private static final MediaCommandActor OWNER =
            new MediaCommandActor(UUID.fromString("00000000-0000-0000-0000-000000000001"), true, true);

    private FakeAssetRepository assets;
    private FakeStorage storage;
    private MediaAdminCommandService admin;
    private MockMvc publicMedia;

    @BeforeEach
    void setUp() {
        assets = new FakeAssetRepository();
        storage = new FakeStorage();
        admin = new MediaAdminCommandService(
                new OwnerOnlyPolicy(),
                new UploadAssetCommandService(
                        assets,
                        storage,
                        new UploadValidationPolicy(1024, 1024),
                        new MediaContentSniffer(),
                        new ChecksumCalculator(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                new ProcessImageAssetCommandService(
                        assets,
                        storage,
                        bytes -> new ImageMetadata(ImageDimensions.of(1200, 800)),
                        PublicMediaAfterAdminMetadataTest::noGeneratedVariants,
                        new ChecksumCalculator(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                assets,
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("publicImageVariantContentService", new PublicImageVariantContentService(assets, storage));
        publicMedia = MockMvcBuilders.standaloneSetup(new PublicMediaAssetController(
                beans.getBeanProvider(PublicImageVariantContentService.class))).build();
    }

    @Test
    void publicVariantEligibilityFollowsAdminAltTextMetadataUpdateAndPrivateRollback() throws Exception {
        Asset image = processedImage("alt-image", AssetVisibility.PRIVATE, null, false);
        assets.save(image);
        storage.storeVariants(image);

        publicMedia.perform(get(variantRoute(image.id(), "thumbnail")))
                .andExpect(status().isNotFound());

        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, image.id(), AssetVisibility.PUBLIC, "Helpful chart", false)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(image.id()));

        publicMedia.perform(get(variantRoute(image.id(), "thumbnail")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FakeStorage.VARIANT_BYTES));

        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, image.id(), AssetVisibility.PRIVATE, "Helpful chart", false)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(image.id()));

        publicMedia.perform(get(variantRoute(image.id(), "thumbnail")))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicVariantEligibilityAllowsDecorativeImageMetadata() throws Exception {
        Asset image = processedImage("decorative-image", AssetVisibility.PRIVATE, null, false);
        assets.save(image);
        storage.storeVariants(image);

        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, image.id(), AssetVisibility.PUBLIC, null, true)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(image.id()));

        publicMedia.perform(get(variantRoute(image.id(), "thumbnail")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FakeStorage.VARIANT_BYTES));
    }

    @Test
    void pendingAndFailedImagesCannotBecomePublicThroughAdminMetadata() {
        Asset pending = Asset.pendingImage(
                AssetId.newId(),
                OriginalFilename.of("pending.png"),
                StoredFilename.of("pending.png"),
                StoragePath.of("original/pending.png"),
                null,
                ContentTypeName.of("image/png"),
                FileExtension.of("png"),
                FileSize.of(10),
                Checksum.of("pending"),
                NOW);
        Asset failed = Asset.pendingImage(
                AssetId.newId(),
                OriginalFilename.of("failed.png"),
                StoredFilename.of("failed.png"),
                StoragePath.of("original/failed.png"),
                null,
                ContentTypeName.of("image/png"),
                FileExtension.of("png"),
                FileSize.of(10),
                Checksum.of("failed"),
                NOW);
        failed.markFailed(List.of(validation("image_processing", ValidationStatus.FAILED)), NOW.plusSeconds(1));
        assets.save(pending);
        assets.save(failed);

        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, pending.id(), AssetVisibility.PUBLIC, "Pending", false)))
                .isInstanceOf(AssetMetadataUpdateResult.Rejected.class);
        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, failed.id(), AssetVisibility.PUBLIC, "Failed", false)))
                .isInstanceOf(AssetMetadataUpdateResult.Rejected.class);
    }

    @Test
    void pdfCanBecomePublicButNoGenericPublicPdfRouteExists() throws Exception {
        Asset pdf = Asset.pdf(
                AssetId.newId(),
                OriginalFilename.of("cv.pdf"),
                StoredFilename.of("cv.pdf"),
                StoragePath.of("original/cv.pdf"),
                null,
                ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"),
                FileSize.of(100),
                Checksum.of("pdf"),
                AssetVisibility.PRIVATE,
                List.of(validation("pdf_signature", ValidationStatus.PASSED)),
                NOW);
        assets.save(pdf);

        assertThat(admin.updateMetadata(new UpdateAssetMetadataCommand(
                OWNER, pdf.id(), AssetVisibility.PUBLIC, null, false)))
                .isEqualTo(new AssetMetadataUpdateResult.Updated(pdf.id()));

        publicMedia.perform(get(publicMediaAssetRoute(pdf.id(), "")))
                .andExpect(status().isNotFound());
        publicMedia.perform(get(publicMediaAssetRoute(pdf.id(), "original")))
                .andExpect(status().isNotFound());
        publicMedia.perform(get(publicMediaAssetRoute(pdf.id(), "download")))
                .andExpect(status().isNotFound());
    }

    private static List<dev.persefonia.medialibrary.application.processing.GeneratedImageVariant> noGeneratedVariants(
            ImageVariantGenerationRequest request) {
        return List.of();
    }

    private static String variantRoute(AssetId assetId, String variantName) {
        return "/media/assets/" + assetId.value() + "/variants/" + variantName;
    }

    private static String publicMediaAssetRoute(AssetId assetId, String suffix) {
        String base = "/media/" + "assets/" + assetId.value();
        return suffix.isBlank() ? base : base + "/" + suffix;
    }

    private static Asset processedImage(
            String key,
            AssetVisibility visibility,
            String altText,
            boolean decorative) {
        AssetId id = AssetId.newId();
        return Asset.processedImage(
                id,
                OriginalFilename.of(key + ".png"),
                StoredFilename.of(key + ".png"),
                StoragePath.of("original/" + id.value() + "/" + key + ".png"),
                null,
                ContentTypeName.of("image/png"),
                FileExtension.of("png"),
                FileSize.of(100),
                Checksum.of(key),
                visibility,
                ImageDimensions.of(1200, 800),
                altText == null ? null : AltText.of(altText),
                decorative ? DecorativeImageFlag.decorative() : DecorativeImageFlag.informative(),
                variants(id),
                List.of(validation("image_decode", ValidationStatus.PASSED)),
                NOW);
    }

    private static List<AssetVariant> variants(AssetId assetId) {
        return List.of(
                variant(assetId, VariantName.THUMBNAIL, 320, 200),
                variant(assetId, VariantName.MEDIUM, 800, 500),
                variant(assetId, VariantName.LARGE, 1200, 800),
                variant(assetId, VariantName.OG, 1200, 630));
    }

    private static AssetVariant variant(AssetId assetId, VariantName name, int width, int height) {
        return new AssetVariant(
                AssetVariantId.newId(),
                name,
                PixelWidth.of(width),
                PixelHeight.of(height),
                ContentTypeName.of("image/png"),
                FileSize.of(FakeStorage.VARIANT_BYTES.length),
                StoragePath.of("variant-test/" + assetId.value() + "/" + name.databaseValue() + ".png"),
                null,
                Checksum.of("variant-" + name.databaseValue()),
                NOW);
    }

    private static AssetValidationResult validation(String rule, ValidationStatus status) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(),
                ValidationRuleName.of(rule),
                status,
                status == ValidationStatus.FAILED ? ValidationMessage.of("Failed") : ValidationMessage.of("Passed"),
                NOW);
    }

    private static final class OwnerOnlyPolicy implements MediaCommandAuthorizationPolicy {
        @Override
        public void requireOwner(MediaCommandActor actor, String commandName) {
            if (actor == null || !actor.active() || !actor.owner()) {
                throw new SecurityException(commandName);
            }
        }
    }

    private static final class FakeAssetRepository implements AssetRepository {
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
    }

    private static final class FakeStorage implements AssetStoragePort {
        private static final byte[] VARIANT_BYTES = "thumbnail-bytes".getBytes();
        private final Map<StoragePath, byte[]> stored = new LinkedHashMap<>();

        void storeVariants(Asset asset) {
            for (AssetVariant variant : asset.variants()) {
                stored.put(variant.storagePath(), VARIANT_BYTES);
            }
        }

        @Override
        public InputStream openStored(StoragePath storagePath) {
            byte[] bytes = stored.get(storagePath);
            if (bytes == null) {
                throw new IllegalStateException("missing stored object");
            }
            return new ByteArrayInputStream(bytes);
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
            stored.put(request.storagePath(), request.content());
            return new StoredAssetObject(request.storagePath().value());
        }

        @Override public void deleteStagedIfExists(StagedAssetObject stagedObject) {
        }

        @Override public void deleteStoredIfExists(StoredAssetObject storedObject) {
        }

        @Override public void deleteStoredByPathIfExists(StoragePath storagePath) {
            stored.remove(storagePath);
        }
    }
}

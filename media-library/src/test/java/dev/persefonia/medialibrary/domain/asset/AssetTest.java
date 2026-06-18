package dev.persefonia.medialibrary.domain.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void privatePendingImageIsValid() {
        Asset asset = pendingImage();

        assertThat(asset.visibility()).isEqualTo(AssetVisibility.PRIVATE);
        assertThat(asset.processingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(asset.imageDimensions()).isEmpty();
    }

    @Test
    void publicPendingImageIsRejected() {
        assertThatThrownBy(() -> rehydrateImage(
                AssetVisibility.PUBLIC, ProcessingStatus.PENDING, null, AltText.of("Portrait"),
                DecorativeImageFlag.informative(), List.of()))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void publicProcessedImageWithAltTextIsValid() {
        Asset asset = processedImage(AltText.of("Portrait"), DecorativeImageFlag.informative());

        assertThat(asset.visibility()).isEqualTo(AssetVisibility.PUBLIC);
    }

    @Test
    void publicProcessedDecorativeImageIsValid() {
        Asset asset = processedImage(null, DecorativeImageFlag.decorative());

        assertThat(asset.decorative().value()).isTrue();
    }

    @Test
    void publicProcessedImageWithoutAccessibilityMetadataIsRejected() {
        assertThatThrownBy(() -> processedImage(null, DecorativeImageFlag.informative()))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void processedImageWithoutDimensionsIsRejectedButFailedImageWithoutDimensionsIsValid() {
        assertThatThrownBy(() -> rehydrateImage(
                AssetVisibility.PRIVATE, ProcessingStatus.PROCESSED, null, null,
                DecorativeImageFlag.informative(), List.of()))
                .isInstanceOf(AssetValidationException.class);
        Asset failed = rehydrateImage(
                AssetVisibility.PRIVATE, ProcessingStatus.FAILED, null, null,
                DecorativeImageFlag.informative(), List.of());
        assertThat(failed.imageDimensions()).isEmpty();
    }

    @Test
    void publicFailedImageIsRejected() {
        assertThatThrownBy(() -> rehydrateImage(
                AssetVisibility.PUBLIC, ProcessingStatus.FAILED, null, AltText.of("Portrait"),
                DecorativeImageFlag.informative(), List.of()))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void pdfWithNotRequiredProcessingIsValidAndNonImagesRejectVariants() {
        Asset pdf = pdf();
        assertThat(pdf.processingStatus()).isEqualTo(ProcessingStatus.NOT_REQUIRED);

        assertThatThrownBy(() -> Asset.rehydrate(
                pdf.id(), pdf.originalFilename(), pdf.storedFilename(), pdf.storagePath(), null,
                pdf.contentType(), pdf.fileExtension(), pdf.sizeBytes(), pdf.checksum(), AssetKind.PDF,
                AssetVisibility.PRIVATE, null, null, DecorativeImageFlag.informative(),
                ProcessingStatus.NOT_REQUIRED, List.of(variant(VariantName.THUMBNAIL)), List.of(),
                NOW, NOW, Version.initial()))
                .isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> Asset.rehydrate(
                pdf.id(), pdf.originalFilename(), pdf.storedFilename(), pdf.storagePath(), null,
                pdf.contentType(), pdf.fileExtension(), pdf.sizeBytes(), pdf.checksum(), AssetKind.DOCUMENT,
                AssetVisibility.PRIVATE, null, null, DecorativeImageFlag.informative(),
                ProcessingStatus.NOT_REQUIRED, List.of(variant(VariantName.THUMBNAIL)), List.of(),
                NOW, NOW, Version.initial()))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void duplicateVariantNamesAndValidationRulesAreRejected() {
        assertThatThrownBy(() -> rehydrateImage(
                AssetVisibility.PRIVATE, ProcessingStatus.PROCESSED, ImageDimensions.of(100, 100), null,
                DecorativeImageFlag.informative(),
                List.of(variant(VariantName.MEDIUM), variant(VariantName.MEDIUM))))
                .isInstanceOf(AssetValidationException.class);

        Asset asset = pendingImage();
        assertThatThrownBy(() -> asset.replaceValidationResults(
                List.of(validation("mime"), validation("mime")), NOW.plusSeconds(1)))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void primitiveMetadataValueObjectsRejectInvalidValues() {
        assertThatThrownBy(() -> OriginalFilename.of(" ")).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> StoredFilename.of(" ")).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> ContentTypeName.of(" ")).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> FileExtension.of(" ")).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> FileSize.of(0)).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> Checksum.of(" ")).isInstanceOf(AssetValidationException.class);
    }

    @Test
    void storagePathRejectsObviousUnsafeMetadata() {
        for (String path : List.of("", "../asset.png", "/asset.png", "media\\asset.png")) {
            assertThatThrownBy(() -> StoragePath.of(path)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> StoragePath.of("media/\0asset.png"))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void rehydrateRejectsUpdatedAtBeforeCreatedAtAndInvalidPersistedState() {
        assertThatThrownBy(() -> Asset.rehydrate(
                AssetId.newId(), OriginalFilename.of("a.png"), StoredFilename.of("a-1.png"),
                StoragePath.of("media/a-1.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(10), Checksum.of("checksum"), AssetKind.IMAGE,
                AssetVisibility.PRIVATE, null, null, DecorativeImageFlag.informative(),
                ProcessingStatus.PENDING, List.of(), List.of(), NOW, NOW.minusSeconds(1), Version.initial()))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    void visibilityMutationsEnforceImageRules() {
        Asset pending = pendingImage();
        assertThatThrownBy(() -> pending.makePublic(NOW.plusSeconds(1)))
                .isInstanceOf(AssetValidationException.class);
        pending.makePrivate(NOW.plusSeconds(1));
        assertThat(pending.visibility()).isEqualTo(AssetVisibility.PRIVATE);

        Asset failed = rehydrateImage(
                AssetVisibility.PRIVATE, ProcessingStatus.FAILED, ImageDimensions.of(100, 100), null,
                DecorativeImageFlag.informative(), List.of());
        failed.makePrivate(NOW.plusSeconds(1));
        assertThat(failed.visibility()).isEqualTo(AssetVisibility.PRIVATE);

        Asset processed = processedImage(AltText.of("Portrait"), DecorativeImageFlag.informative());
        processed.makePrivate(NOW.plusSeconds(1));
        processed.makePublic(NOW.plusSeconds(2));
        assertThat(processed.visibility()).isEqualTo(AssetVisibility.PUBLIC);
    }

    @Test
    void replacingEachChildCollectionIncrementsVersionOnce() {
        Asset asset = pendingImage();

        asset.replaceVariants(List.of(variant(VariantName.THUMBNAIL)), NOW.plusSeconds(1));
        assertThat(asset.version().value()).isEqualTo(1);

        asset.replaceValidationResults(List.of(validation("mime")), NOW.plusSeconds(2));
        assertThat(asset.version().value()).isEqualTo(2);
    }

    private static Asset pendingImage() {
        return Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of("portrait.png"), StoredFilename.of("portrait-1.png"),
                StoragePath.of("media/portrait-1.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(100), Checksum.of("pending-checksum"), NOW);
    }

    private static Asset processedImage(AltText altText, DecorativeImageFlag decorative) {
        return Asset.processedImage(
                AssetId.newId(), OriginalFilename.of("portrait.png"), StoredFilename.of("portrait-2.png"),
                StoragePath.of("media/portrait-2.png"), PublicAssetUrl.of("/media/portrait-2.png"),
                ContentTypeName.of("image/png"), FileExtension.of("png"), FileSize.of(100),
                Checksum.of("processed-" + decorative.value() + "-" + (altText == null ? "none" : "alt")),
                AssetVisibility.PUBLIC, ImageDimensions.of(800, 600), altText, decorative,
                List.of(), List.of(), NOW);
    }

    private static Asset pdf() {
        return Asset.pdf(
                AssetId.newId(), OriginalFilename.of("cv.pdf"), StoredFilename.of("cv-1.pdf"),
                StoragePath.of("media/cv-1.pdf"), null, ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"), FileSize.of(100), Checksum.of("pdf-checksum"),
                AssetVisibility.PRIVATE, List.of(), NOW);
    }

    private static Asset rehydrateImage(
            AssetVisibility visibility,
            ProcessingStatus status,
            ImageDimensions dimensions,
            AltText altText,
            DecorativeImageFlag decorative,
            List<AssetVariant> variants) {
        return Asset.rehydrate(
                AssetId.newId(), OriginalFilename.of("a.png"), StoredFilename.of("a-1.png"),
                StoragePath.of("media/a-1.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(10), Checksum.of("checksum-" + System.nanoTime()),
                AssetKind.IMAGE, visibility, dimensions, altText, decorative, status, variants,
                List.of(), NOW, NOW, Version.initial());
    }

    private static AssetVariant variant(VariantName name) {
        return new AssetVariant(
                AssetVariantId.newId(), name, PixelWidth.of(100), PixelHeight.of(100),
                ContentTypeName.of("image/webp"), FileSize.of(20),
                StoragePath.of("media/" + name.databaseValue() + "-" + System.nanoTime() + ".webp"),
                null, Checksum.of("variant-" + System.nanoTime()), NOW);
    }

    private static AssetValidationResult validation(String rule) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(), ValidationRuleName.of(rule), ValidationStatus.PASSED,
                null, NOW);
    }
}

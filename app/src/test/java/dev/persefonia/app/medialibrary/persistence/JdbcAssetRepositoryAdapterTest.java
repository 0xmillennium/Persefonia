package dev.persefonia.app.medialibrary.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetValidationException;
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
import dev.persefonia.medialibrary.domain.asset.PublicAssetUrl;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.ValidationMessage;
import dev.persefonia.medialibrary.domain.asset.ValidationRuleName;
import dev.persefonia.medialibrary.domain.asset.ValidationStatus;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcAssetRepositoryAdapterTest extends MediaLibraryRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void savesAndReloadsPrivatePendingImage() {
        Asset saved = assets.save(pendingImage("pending"));

        Asset reloaded = assets.findById(saved.id()).orElseThrow();

        assertThat(reloaded.processingStatus()).isEqualTo(saved.processingStatus());
        assertThat(reloaded.version()).isEqualTo(saved.version());
    }

    @Test
    void savesAndReloadsPublicProcessedInformativeAndDecorativeImages() {
        Asset informative = assets.save(processedImage("informative", AltText.of("Portrait"),
                DecorativeImageFlag.informative(), List.of(), List.of()));
        Asset decorative = assets.save(processedImage("decorative", null,
                DecorativeImageFlag.decorative(), List.of(), List.of()));

        assertThat(assets.findById(informative.id()).orElseThrow().altText()).contains(AltText.of("Portrait"));
        assertThat(assets.findById(decorative.id()).orElseThrow().decorative().value()).isTrue();
    }

    @Test
    void savesAndReloadsPdfWithNotRequiredProcessing() {
        Asset pdf = assets.save(Asset.pdf(
                AssetId.newId(), OriginalFilename.of("cv.pdf"), StoredFilename.of("cv.pdf"),
                StoragePath.of("media/cv.pdf"), null, ContentTypeName.of("application/pdf"),
                FileExtension.of("pdf"), FileSize.of(100), Checksum.of("pdf"),
                AssetVisibility.PRIVATE, List.of(validation("pdf-signature")), NOW));

        assertThat(assets.findById(pdf.id()).orElseThrow().validationResults()).hasSize(1);
    }

    @Test
    void savesReloadsAndReplacesOwnedChildrenWithoutStaleRows() {
        Asset asset = processedImage(
                "children", AltText.of("Portrait"), DecorativeImageFlag.informative(),
                List.of(variant(VariantName.THUMBNAIL, "thumb"), variant(VariantName.MEDIUM, "medium")),
                List.of(validation("mime"), validation("dimensions")));
        Asset saved = assets.save(asset);
        assertThat(saved.variants()).extracting(AssetVariant::name)
                .containsExactly(VariantName.THUMBNAIL, VariantName.MEDIUM);
        assertThat(saved.validationResults()).extracting(result -> result.rule().value())
                .containsExactly("dimensions", "mime");

        saved.replaceVariants(List.of(variant(VariantName.LARGE, "large")), NOW.plusSeconds(1));
        saved.replaceValidationResults(List.of(validation("checksum")), NOW.plusSeconds(2));
        Asset replaced = assets.save(saved);

        assertThat(replaced.variants()).extracting(AssetVariant::name).containsExactly(VariantName.LARGE);
        assertThat(replaced.validationResults()).extracting(result -> result.rule().value())
                .containsExactly("checksum");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.asset_variants", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media.asset_validation_results", Long.class)).isEqualTo(1);
        assertThat(replaced.version().value()).isEqualTo(2);
    }

    @Test
    void findsByChecksumAndReturnsEmptyForMissingValues() {
        Asset saved = assets.save(pendingImage("lookup"));

        assertThat(assets.findByChecksum(saved.checksum())).map(Asset::id).contains(saved.id());
        assertThat(assets.findByChecksum(Checksum.of("missing"))).isEmpty();
    }

    @Test
    void duplicateChecksumIsRejectedByDatabase() {
        assets.save(pendingImage("duplicate"));

        assertThatThrownBy(() -> assets.save(pendingImage("duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void domainRejectsDuplicateChildrenBeforePersistence() {
        assertThatThrownBy(() -> processedImage(
                "duplicate-variant", AltText.of("Portrait"), DecorativeImageFlag.informative(),
                List.of(variant(VariantName.MEDIUM, "one"), variant(VariantName.MEDIUM, "two")), List.of()))
                .isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(() -> processedImage(
                "duplicate-rule", AltText.of("Portrait"), DecorativeImageFlag.informative(),
                List.of(), List.of(validation("mime"), validation("mime"))))
                .isInstanceOf(AssetValidationException.class);
    }

    private static Asset pendingImage(String key) {
        return Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of(key + ".png"), StoredFilename.of(key + ".png"),
                StoragePath.of("media/" + key + "-" + System.nanoTime() + ".png"), null,
                ContentTypeName.of("image/png"), FileExtension.of("png"), FileSize.of(100),
                Checksum.of(key), NOW);
    }

    private static Asset processedImage(
            String key,
            AltText altText,
            DecorativeImageFlag decorative,
            List<AssetVariant> variants,
            List<AssetValidationResult> validationResults) {
        return Asset.processedImage(
                AssetId.newId(), OriginalFilename.of(key + ".png"), StoredFilename.of(key + ".png"),
                StoragePath.of("media/" + key + ".png"), PublicAssetUrl.of("/media/" + key + ".png"),
                ContentTypeName.of("image/png"), FileExtension.of("png"), FileSize.of(100),
                Checksum.of(key), AssetVisibility.PUBLIC, ImageDimensions.of(800, 600),
                altText, decorative, variants, validationResults, NOW);
    }

    private static AssetVariant variant(VariantName name, String key) {
        return new AssetVariant(
                AssetVariantId.newId(), name, PixelWidth.of(100), PixelHeight.of(100),
                ContentTypeName.of("image/webp"), FileSize.of(20), StoragePath.of("media/" + key + ".webp"),
                PublicAssetUrl.of("/media/" + key + ".webp"), Checksum.of("variant-" + key), NOW);
    }

    private static AssetValidationResult validation(String rule) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(), ValidationRuleName.of(rule), ValidationStatus.PASSED,
                ValidationMessage.of("Passed"), NOW);
    }
}

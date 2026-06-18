package dev.persefonia.app.medialibrary.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
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
import org.springframework.beans.factory.annotation.Autowired;

class JdbcMediaAdminReadModelAdapterTest extends MediaLibraryRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Autowired MediaAdminReadModel readModel;

    @Test
    void listsAssetsSortedByUpdatedThenCreatedDescending() {
        Asset older = assets.save(processedImage("older", AssetVisibility.PRIVATE, NOW));
        Asset newer = processedImage("newer", AssetVisibility.PRIVATE, NOW.plusSeconds(5));
        newer.makePrivate(NOW.plusSeconds(20));
        newer = assets.save(newer);

        assertThat(readModel.listAssets()).extracting(item -> item.assetId())
                .containsExactly(newer.id(), older.id());
    }

    @Test
    void detailMapsVariantsValidationAndPublicRoutesWithoutRawStoragePath() {
        Asset saved = assets.save(processedImage("public", AssetVisibility.PUBLIC, NOW));

        var details = readModel.findAssetDetails(saved.id()).orElseThrow();

        assertThat(details.summary().originalFilename()).isEqualTo("public.png");
        assertThat(details.summary().dimensionsLabel()).isEqualTo("800 x 600");
        assertThat(details.altText()).isEqualTo("Alt public");
        assertThat(details.variants()).singleElement().satisfies(variant -> {
            assertThat(variant.name()).isEqualTo("thumbnail");
            assertThat(variant.publicRouteOptional())
                    .contains("/media/assets/" + saved.id().value() + "/variants/thumbnail");
            assertThat(variant.toString()).doesNotContain("storage_path").doesNotContain("storagePath");
        });
        assertThat(details.validationResults()).singleElement().satisfies(validation -> {
            assertThat(validation.rule()).isEqualTo("image_decode");
            assertThat(validation.status()).isEqualTo("PASSED");
        });
    }

    @Test
    void missingDetailReturnsEmpty() {
        assertThat(readModel.findAssetDetails(AssetId.newId())).isEmpty();
    }

    private static Asset processedImage(String key, AssetVisibility visibility, Instant now) {
        return Asset.processedImage(
                AssetId.newId(),
                OriginalFilename.of(key + ".png"),
                StoredFilename.of(key + ".png"),
                StoragePath.of("original/" + key + ".png"),
                PublicAssetUrl.of("/media/" + key + ".png"),
                ContentTypeName.of("image/png"),
                FileExtension.of("png"),
                FileSize.of(100),
                Checksum.of(key),
                visibility,
                ImageDimensions.of(800, 600),
                AltText.of("Alt " + key),
                DecorativeImageFlag.informative(),
                List.of(variant(key)),
                List.of(validation()),
                now);
    }

    private static AssetVariant variant(String key) {
        return new AssetVariant(
                AssetVariantId.newId(),
                VariantName.THUMBNAIL,
                PixelWidth.of(120),
                PixelHeight.of(80),
                ContentTypeName.of("image/png"),
                FileSize.of(50),
                StoragePath.of("variants/" + key + ".png"),
                PublicAssetUrl.of("/media/" + key + ".png"),
                Checksum.of("variant-" + key),
                NOW);
    }

    private static AssetValidationResult validation() {
        return new AssetValidationResult(
                AssetValidationResultId.newId(),
                ValidationRuleName.of("image_decode"),
                ValidationStatus.PASSED,
                ValidationMessage.of("ok"),
                NOW);
    }
}

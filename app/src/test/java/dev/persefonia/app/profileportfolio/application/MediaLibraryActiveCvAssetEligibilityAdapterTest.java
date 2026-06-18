package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.medialibrary.application.MediaLibraryActiveCvAssetEligibilityAdapter;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReference;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaLibraryActiveCvAssetEligibilityAdapterTest {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void bridgesPublicPdfReferencesToProfilePortfolioEligibleAssetsWithoutStoragePaths() {
        AssetId mediaId = AssetId.from(UUID.randomUUID());
        var adapter = new MediaLibraryActiveCvAssetEligibilityAdapter(new PublicPdfAssetQueryService(readModel(mediaId)));

        var asset = adapter.findEligiblePublicPdf(MediaAssetId.from(mediaId.value())).orElseThrow();

        assertThat(asset.mediaAssetId().value()).isEqualTo(mediaId.value());
        assertThat(asset.contentType()).isEqualTo("application/pdf");
        assertThat(asset.toString()).doesNotContain("storage").doesNotContain("path");
        assertThat(adapter.listEligiblePublicPdfCandidates()).hasSize(1);
    }

    @Test
    void missingAssetReturnsEmpty() {
        var adapter = new MediaLibraryActiveCvAssetEligibilityAdapter(new PublicPdfAssetQueryService(readModel(AssetId.newId())));

        assertThat(adapter.findEligiblePublicPdf(MediaAssetId.from(UUID.randomUUID()))).isEmpty();
    }

    private static PublicPdfAssetReadModel readModel(AssetId eligibleId) {
        PublicPdfAssetReference reference = new PublicPdfAssetReference(
                eligibleId, "cv.pdf", "application/pdf", 100, NOW);
        return new PublicPdfAssetReadModel() {
            @Override
            public Optional<PublicPdfAssetReference> findEligiblePublicPdf(AssetId assetId) {
                return eligibleId.equals(assetId) ? Optional.of(reference) : Optional.empty();
            }

            @Override
            public List<PublicPdfAssetReference> listEligiblePublicPdfs() {
                return List.of(reference);
            }
        };
    }
}

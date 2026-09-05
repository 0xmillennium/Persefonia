package dev.persefonia.app.medialibrary.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.DecorativeImageFlag;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.PublicAssetUrl;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.Version;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcPublicPdfAssetReadModelAdapterTest extends MediaLibraryRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @Autowired PublicPdfAssetReadModel readModel;

    @Test
    void publicPdfIsEligible() {
        Asset pdf = assets.save(pdf("public-pdf", AssetVisibility.PUBLIC, "application/pdf"));

        assertThat(readModel.findEligiblePublicPdf(pdf.id())).isPresent();
    }

    @Test
    void privatePdfIsIneligible() {
        Asset pdf = assets.save(pdf("private-pdf", AssetVisibility.PRIVATE, "application/pdf"));

        assertThat(readModel.findEligiblePublicPdf(pdf.id())).isEmpty();
    }

    @Test
    void publicImagePrivateImagePendingImageFailedImageDocumentAndNonPdfContentTypeAreIneligible() {
        Asset publicImage = assets.save(processedImage("public-image", AssetVisibility.PUBLIC));
        Asset privateImage = assets.save(processedImage("private-image", AssetVisibility.PRIVATE));
        Asset pendingImage = assets.save(Asset.pendingImage(
                AssetId.newId(), OriginalFilename.of("pending.png"), StoredFilename.of("pending.png"),
                StoragePath.of("original/pending.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(100), Checksum.of("pending"), NOW));
        Asset failedImage = assets.save(failedImage());
        Asset document = assets.save(Asset.document(
                AssetId.newId(), OriginalFilename.of("document.txt"), StoredFilename.of("document.txt"),
                StoragePath.of("original/document.txt"), null, ContentTypeName.of("text/plain"),
                FileExtension.of("txt"), FileSize.of(100), Checksum.of("document"), AssetVisibility.PUBLIC,
                List.of(), NOW));
        Asset pdfWrongType = assets.save(pdf("wrong-type", AssetVisibility.PUBLIC, "application/octet-stream"));

        assertThat(readModel.findEligiblePublicPdf(publicImage.id())).isEmpty();
        assertThat(readModel.findEligiblePublicPdf(privateImage.id())).isEmpty();
        assertThat(readModel.findEligiblePublicPdf(pendingImage.id())).isEmpty();
        assertThat(readModel.findEligiblePublicPdf(failedImage.id())).isEmpty();
        assertThat(readModel.findEligiblePublicPdf(document.id())).isEmpty();
        assertThat(readModel.findEligiblePublicPdf(pdfWrongType.id())).isEmpty();
    }

    @Test
    void candidateListReturnsOnlyPublicPdfsAndDoesNotExposeStoragePath() {
        Asset pdf = assets.save(pdf("candidate", AssetVisibility.PUBLIC, "application/pdf"));
        assets.save(pdf("private", AssetVisibility.PRIVATE, "application/pdf"));

        var candidates = readModel.listEligiblePublicPdfs();

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.assetId()).isEqualTo(pdf.id());
            assertThat(candidate.toString()).doesNotContain("storage").doesNotContain("path");
        });
    }

    @Test
    void missingAssetIsIneligible() {
        assertThat(readModel.findEligiblePublicPdf(AssetId.newId())).isEmpty();
    }

    private static Asset pdf(String key, AssetVisibility visibility, String contentType) {
        return Asset.pdf(
                AssetId.newId(), OriginalFilename.of(key + ".pdf"), StoredFilename.of(key + ".pdf"),
                StoragePath.of("original/" + key + ".pdf"), null, ContentTypeName.of(contentType),
                FileExtension.of("pdf"), FileSize.of(100), Checksum.of(key), visibility, List.of(), NOW);
    }

    private static Asset processedImage(String key, AssetVisibility visibility) {
        return Asset.processedImage(
                AssetId.newId(), OriginalFilename.of(key + ".png"), StoredFilename.of(key + ".png"),
                StoragePath.of("original/" + key + ".png"), PublicAssetUrl.of("/media/" + key + ".png"),
                ContentTypeName.of("image/png"), FileExtension.of("png"), FileSize.of(100),
                Checksum.of(key), visibility, ImageDimensions.of(800, 600), AltText.of("Alt"),
                DecorativeImageFlag.informative(), List.of(), List.of(), NOW);
    }

    private static Asset failedImage() {
        return Asset.rehydrate(
                AssetId.newId(), OriginalFilename.of("failed.png"), StoredFilename.of("failed.png"),
                StoragePath.of("original/failed.png"), null, ContentTypeName.of("image/png"),
                FileExtension.of("png"), FileSize.of(100), Checksum.of("failed"),
                dev.persefonia.medialibrary.domain.asset.AssetKind.IMAGE, AssetVisibility.PRIVATE, null, null,
                DecorativeImageFlag.informative(), ProcessingStatus.FAILED, List.of(), List.of(),
                NOW, NOW, Version.initial());
    }
}

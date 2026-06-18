package dev.persefonia.app.webadmin.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetListItem;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.webadmin.media.AdminMediaFormValidator;
import dev.persefonia.webadmin.media.AdminMediaMetadataForm;
import dev.persefonia.webadmin.media.AdminMediaUploadForm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AdminMediaFormValidatorTest {
    private final AdminMediaFormValidator validator = new AdminMediaFormValidator();

    @Test
    void emptyUploadAndMissingFilenameAreRejected() {
        AdminMediaUploadForm empty = new AdminMediaUploadForm();
        empty.setFile(new MockMultipartFile("file", "photo.png", "image/png", new byte[0]));
        AdminMediaUploadForm missingName = new AdminMediaUploadForm();
        missingName.setFile(new MockMultipartFile("file", "", "image/png", "x".getBytes()));

        assertThat(validator.validateUpload(empty)).extracting("field").containsExactly("file");
        assertThat(validator.validateUpload(missingName)).extracting("field").containsExactly("file");
    }

    @Test
    void metadataVisibilityIsParsedAndInvalidVisibilityRejected() {
        AdminMediaMetadataForm form = new AdminMediaMetadataForm();
        form.setVisibility("PUBLIC");

        assertThat(validator.validateMetadata(form, details(AssetKind.IMAGE))).isEmpty();

        form.setVisibility("VISIBLE");
        assertThat(validator.validateMetadata(form, details(AssetKind.IMAGE)))
                .extracting("field")
                .containsExactly("visibility");
    }

    @Test
    void pdfMetadataDoesNotRequireAltTextOrDecorativeButRejectsImageOnlyFields() {
        AdminMediaMetadataForm form = new AdminMediaMetadataForm();
        form.setVisibility("PUBLIC");
        assertThat(validator.validateMetadata(form, details(AssetKind.PDF))).isEmpty();

        form.setAltText("PDF alt");
        form.setDecorative(true);
        assertThat(validator.validateMetadata(form, details(AssetKind.PDF)))
                .extracting("field")
                .containsExactly("decorative", "altText");
    }

    private static MediaAdminAssetDetails details(AssetKind kind) {
        return new MediaAdminAssetDetails(
                new MediaAdminAssetListItem(
                        AssetId.from(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                        "asset",
                        kind,
                        AssetVisibility.PRIVATE,
                        kind == AssetKind.IMAGE ? ProcessingStatus.PROCESSED : ProcessingStatus.NOT_REQUIRED,
                        kind == AssetKind.PDF ? "application/pdf" : "image/png",
                        kind == AssetKind.PDF ? "pdf" : "png",
                        10,
                        "checksum",
                        kind == AssetKind.IMAGE ? 10 : null,
                        kind == AssetKind.IMAGE ? 10 : null,
                        Instant.parse("2026-06-18T10:00:00Z"),
                        Instant.parse("2026-06-18T10:00:00Z")),
                null,
                false,
                List.of(),
                List.of());
    }
}

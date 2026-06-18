package dev.persefonia.app.webadmin.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.webadmin.media.AdminMediaFormMapper;
import dev.persefonia.webadmin.media.AdminMediaMetadataForm;
import dev.persefonia.webadmin.media.AdminMediaUploadForm;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AdminMediaFormMapperTest {
    private final AdminMediaFormMapper mapper = new AdminMediaFormMapper();
    private final MediaCommandActor owner =
            new MediaCommandActor(UUID.fromString("00000000-0000-0000-0000-000000000001"), true, true);

    @Test
    void multipartFileMapsToFrameworkFreeUploadCommandWithSafeExtension() throws Exception {
        AdminMediaUploadForm form = new AdminMediaUploadForm();
        form.setFile(new MockMultipartFile(
                "file",
                "../Portrait.JPEG",
                "image/jpeg",
                "image".getBytes()));

        var command = mapper.toUploadCommand(owner, form);

        assertThat(command.actor()).isEqualTo(owner);
        assertThat(command.originalFilename()).isEqualTo("../Portrait.JPEG");
        assertThat(command.declaredContentType()).isEqualTo("image/jpeg");
        assertThat(command.declaredExtension()).isEqualTo("jpeg");
        assertThat(command.declaredSize()).isEqualTo(5);
        try (InputStream input = command.byteSource().openStream()) {
            assertThat(input.readAllBytes()).isEqualTo("image".getBytes());
        }
    }

    @Test
    void metadataFormMapsVisibilityAltTextAndDecorativeFlag() {
        AdminMediaMetadataForm form = new AdminMediaMetadataForm();
        form.setVisibility("public");
        form.setAltText("A chart");
        form.setDecorative(false);
        AssetId assetId = AssetId.from(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        var command = mapper.toMetadataCommand(owner, assetId, form);

        assertThat(command.assetId()).isEqualTo(assetId);
        assertThat(command.requestedVisibility()).isEqualTo(AssetVisibility.PUBLIC);
        assertThat(command.altText()).isEqualTo("A chart");
        assertThat(command.decorative()).isFalse();
    }
}

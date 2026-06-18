package dev.persefonia.app.webadmin.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.media.AdminMediaTestConfiguration.AdminMediaGatewayStub;
import dev.persefonia.app.webadmin.media.AdminMediaTestConfiguration.AdminMediaReadModelStub;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandError;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(AdminMediaTestConfiguration.class)
@ActiveProfiles({"test", "admin-media-mvc-test"})
class AdminMediaControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminMediaReadModelStub readModel;
    @Autowired AdminMediaGatewayStub gateway;

    @BeforeEach
    void reset() {
        readModel.reset();
        gateway.reset();
        readModel.add(AdminMediaTestConfiguration.processedImage());
        readModel.add(AdminMediaTestConfiguration.pdf());
        readModel.add(AdminMediaTestConfiguration.failedImage());
        readModel.add(AdminMediaTestConfiguration.duplicateImage());
    }

    @Test
    void listRendersSafeAssetFields() throws Exception {
        mockMvc.perform(get("/admin/media").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("hero.png")))
                .andExpect(content().string(containsString("cv.pdf")))
                .andExpect(content().string(containsString("PROCESSED")))
                .andExpect(content().string(containsString("NOT_REQUIRED")))
                .andExpect(content().string(containsString("View/edit")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("StoragePath"))))
                .andExpect(content().string(not(containsString("original/"))))
                .andExpect(content().string(not(containsString("variants/"))))
                .andExpect(content().string(not(containsString("delete"))))
                .andExpect(content().string(not(containsString("reprocess"))))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("defaultOgImageAssetId"))))
                .andExpect(content().string(not(containsString("defaultOpenGraphImageAssetId"))));
    }

    @Test
    void newFormRendersMultipartUploadFormWithCsrf() throws Exception {
        mockMvc.perform(get("/admin/media/new").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Upload media")))
                .andExpect(content().string(containsString("<form method=\"post\" enctype=\"multipart/form-data\" action=\"/admin/media\">")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Allowed types: JPEG, PNG, PDF.")))
                .andExpect(content().string(not(containsString("visibility"))))
                .andExpect(content().string(not(containsString("altText"))))
                .andExpect(content().string(not(containsString("decorative"))));
    }

    @Test
    void validImageUploadRedirectsToDetail() throws Exception {
        gateway.nextUploadResult(new AdminUploadAssetResult.Created(
                AdminMediaTestConfiguration.IMAGE_ID, ProcessingStatus.PROCESSED, null));

        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value() + "?uploaded"));

        assertThat(gateway.uploadMutationCount()).isEqualTo(1);
    }

    @Test
    void validPdfUploadRedirectsToDetail() throws Exception {
        gateway.nextUploadResult(new AdminUploadAssetResult.Created(
                AdminMediaTestConfiguration.PDF_ID, ProcessingStatus.NOT_REQUIRED, null));

        mockMvc.perform(multipart("/admin/media").file(pdf()).with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/media/" + AdminMediaTestConfiguration.PDF_ID.value() + "?uploaded"));
    }

    @Test
    void rejectedUploadRedisplaysUploadPageWithErrors() throws Exception {
        gateway.nextUploadResult(new AdminUploadAssetResult.Rejected(List.of(
                AdminMediaTestConfiguration.rejectedUploadError())));

        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("file: Choose a JPEG, PNG, or PDF file.")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));
    }

    @Test
    void duplicateUploadRedirectsToExistingDetail() throws Exception {
        gateway.nextUploadResult(new AdminUploadAssetResult.Duplicate(AdminMediaTestConfiguration.DUPLICATE_ID));

        mockMvc.perform(multipart("/admin/media").file(jpeg()).with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/media/" + AdminMediaTestConfiguration.DUPLICATE_ID.value() + "?duplicate"));
    }

    @Test
    void detailRendersEditFormVariantsAndValidationResultsSafely() throws Exception {
        mockMvc.perform(get("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero.png")))
                .andExpect(content().string(containsString("Asset metadata")))
                .andExpect(content().string(containsString("Variants")))
                .andExpect(content().string(containsString("thumbnail")))
                .andExpect(content().string(containsString("Validation results")))
                .andExpect(content().string(containsString("image_decode")))
                .andExpect(content().string(containsString("action=\"/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value() + "\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"visibility\"")))
                .andExpect(content().string(containsString("name=\"altText\"")))
                .andExpect(content().string(containsString("name=\"decorative\"")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("StoragePath"))))
                .andExpect(content().string(not(containsString("original/"))))
                .andExpect(content().string(not(containsString("variants/"))))
                .andExpect(content().string(not(containsString(adminMediaForbiddenRoute("original")))))
                .andExpect(content().string(not(containsString(adminMediaForbiddenRoute("preview")))))
                .andExpect(content().string(not(containsString(adminMediaForbiddenRoute("variants")))));
    }

    @Test
    void missingDetailReturnsNotFound() throws Exception {
        mockMvc.perform(get("/admin/media/" + AdminMediaTestConfiguration.MISSING_ID.value()).with(owner()))
                .andExpect(status().isNotFound());
    }

    @Test
    void metadataUpdateRedirectsToDetail() throws Exception {
        gateway.nextUpdateResult(new AssetMetadataUpdateResult.Updated(AdminMediaTestConfiguration.IMAGE_ID));

        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner()).with(csrf())
                        .param("visibility", "PUBLIC")
                        .param("altText", "Useful image"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/media/*?saved"));

        assertThat(gateway.updateMutationCount()).isEqualTo(1);
    }

    @Test
    void metadataValidationErrorRedisplaysDetail() throws Exception {
        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner()).with(csrf())
                        .param("visibility", "VISIBLE")
                        .param("altText", "Useful image"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("visibility: Choose PRIVATE or PUBLIC.")))
                .andExpect(content().string(containsString("hero.png")));

        assertThat(gateway.updateMutationCount()).isZero();
    }

    @Test
    void commandRejectionRedisplaysDetailWithErrors() throws Exception {
        gateway.nextUpdateResult(new AssetMetadataUpdateResult.Rejected(List.of(
                new MediaAdminCommandError("visibility", "public image must have alt text or be decorative"))));

        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner()).with(csrf())
                        .param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("visibility: public image must have alt text or be decorative")));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "hero.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.7".getBytes());
    }

    private static String adminMediaForbiddenRoute(String suffix) {
        return "/admin/" + "media/" + AdminMediaTestConfiguration.IMAGE_ID.value() + "/" + suffix;
    }
}

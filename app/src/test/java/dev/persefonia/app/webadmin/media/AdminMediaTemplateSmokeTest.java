package dev.persefonia.app.webadmin.media;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.media.AdminMediaTestConfiguration.AdminMediaReadModelStub;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(AdminMediaTestConfiguration.class)
@ActiveProfiles({"test", "admin-media-template-test"})
class AdminMediaTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminMediaReadModelStub readModel;

    @BeforeEach
    void reset() {
        readModel.reset();
    }

    @Test
    void listTemplateRendersEmptyStateImageAndPdfRows() throws Exception {
        mockMvc.perform(get("/admin/media").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No media assets yet.")))
                .andExpect(content().string(not(containsString("storage_path"))));

        readModel.add(AdminMediaTestConfiguration.processedImage());
        readModel.add(AdminMediaTestConfiguration.pdf());

        mockMvc.perform(get("/admin/media").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero.png")))
                .andExpect(content().string(containsString("IMAGE")))
                .andExpect(content().string(containsString("cv.pdf")))
                .andExpect(content().string(containsString("PDF")))
                .andExpect(content().string(not(containsString("original/"))))
                .andExpect(content().string(not(containsString("variants/"))))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("defaultOgImageAssetId"))))
                .andExpect(content().string(not(containsString("defaultOpenGraphImageAssetId"))))
                .andExpect(content().string(not(containsString("ActiveCv"))));
    }

    @Test
    void newTemplateRendersMultipartUploadFormOnly() throws Exception {
        mockMvc.perform(get("/admin/media/new").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<form method=\"post\" enctype=\"multipart/form-data\" action=\"/admin/media\">")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("type=\"file\"")))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("defaultOgImageAssetId"))))
                .andExpect(content().string(not(containsString("Active CV"))));
    }

    @Test
    void detailTemplateRendersImageMetadataVariantsValidationAndErrorsSafely() throws Exception {
        readModel.add(AdminMediaTestConfiguration.processedImage());

        mockMvc.perform(get("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"visibility\"")))
                .andExpect(content().string(containsString("name=\"altText\"")))
                .andExpect(content().string(containsString("name=\"decorative\"")))
                .andExpect(content().string(containsString("thumbnail")))
                .andExpect(content().string(containsString("image_decode")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("original/" + AdminMediaTestConfiguration.IMAGE_ID.value()))))
                .andExpect(content().string(not(containsString("variants/" + AdminMediaTestConfiguration.IMAGE_ID.value()))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/reprocess"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString(adminMediaForbiddenRoute("original")))))
                .andExpect(content().string(not(containsString(adminMediaForbiddenRoute("variants")))));

        mockMvc.perform(post("/admin/media/" + AdminMediaTestConfiguration.IMAGE_ID.value())
                        .with(owner()).with(csrf())
                        .param("visibility", "VISIBLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("visibility: Choose PRIVATE or PUBLIC.")));
    }

    @Test
    void detailTemplateRendersPdfMetadataWithoutImageAccessibilityControls() throws Exception {
        readModel.add(AdminMediaTestConfiguration.pdf());

        mockMvc.perform(get("/admin/media/" + AdminMediaTestConfiguration.PDF_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cv.pdf")))
                .andExpect(content().string(containsString("name=\"visibility\"")))
                .andExpect(content().string(not(containsString("name=\"altText\""))))
                .andExpect(content().string(not(containsString("name=\"decorative\""))))
                .andExpect(content().string(not(containsString("original/"))))
                .andExpect(content().string(not(containsString("variants/"))))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("defaultOpenGraphImageAssetId"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static String adminMediaForbiddenRoute(String suffix) {
        return "/admin/" + "media/" + AdminMediaTestConfiguration.IMAGE_ID.value() + "/" + suffix;
    }
}

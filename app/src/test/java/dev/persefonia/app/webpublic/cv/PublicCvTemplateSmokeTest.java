package dev.persefonia.app.webpublic.cv;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class PublicCvTemplateSmokeTest {
    private PublicCvMockMvcSupport support;
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        support = PublicCvMockMvcSupport.create();
        support.reset();
        mockMvc = support.mockMvc;
    }

    @Test
    void templateRendersOnlySafePublicCvContent() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                CvDisplayLabel.of("Portfolio CV"),
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Portfolio CV")))
                .andExpect(content().string(containsString("Download cv-en.pdf")))
                .andExpect(content().string(containsString("application/pdf")))
                .andExpect(content().string(not(containsString("storagePath"))))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("publicUrl"))))
                .andExpect(content().string(not(containsString("public_url"))))
                .andExpect(content().string(not(containsString("/media/assets/"))))
                .andExpect(content().string(not(containsString("<iframe"))))
                .andExpect(content().string(not(containsString("<embed"))))
                .andExpect(content().string(not(containsString("<object"))))
                .andExpect(content().string(not(containsString("/admin"))));
    }
}

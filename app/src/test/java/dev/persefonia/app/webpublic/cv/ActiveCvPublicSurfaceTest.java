package dev.persefonia.app.webpublic.cv;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class ActiveCvPublicSurfaceTest {
    private PublicCvMockMvcSupport support;
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        support = PublicCvMockMvcSupport.create();
        support.reset();
        mockMvc = support.mockMvc;
    }

    @Test
    void cvRouteExistsOnlyThroughActiveSelectionAndResumeRemainsAbsent() throws Exception {
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());

        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cv-en.pdf")));
        mockMvc.perform(get("/resume")).andExpect(status().isNotFound());
    }

    @Test
    void publicPdfOriginalAndDownloadRoutesDoNotExist() throws Exception {
        String assetId = PublicCvTestConfiguration.EN_PDF_ID.value().toString();

        mockMvc.perform(get("/media/assets/{assetId}", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/download", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/original", assetId)).andExpect(status().isNotFound());
    }
}

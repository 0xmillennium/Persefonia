package dev.persefonia.app.webpublic.cv;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class PublicCvSecurityAndCacheTest {
    private PublicCvMockMvcSupport support;
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        support = PublicCvMockMvcSupport.create();
        support.reset();
        mockMvc = support.mockMvc;
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);
        support.profiles.profile().selectDocument(
                ContentLanguage.TR,
                PublicCvTestConfiguration.TR_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);
    }

    @Test
    void anonymousPageAndDownloadRoutesAreAllowedWhenActiveCvExists() throws Exception {
        mockMvc.perform(get("/cv")).andExpect(status().isOk());
        mockMvc.perform(get("/cv/tr")).andExpect(status().isOk());
        mockMvc.perform(get("/cv/download")).andExpect(status().isOk());
        mockMvc.perform(get("/cv/tr/download")).andExpect(status().isOk());
    }

    @Test
    void pageAndDownloadCacheHeadersAreShortAndPublic() throws Exception {
        mockMvc.perform(get("/cv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))));

        mockMvc.perform(get("/cv/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=300, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cv-en.pdf\""));
    }

    @Test
    void unsafeStatesReturnNotFound() throws Exception {
        mockMvc.perform(get("/cv/de")).andExpect(status().isNotFound());

        support.profiles.reset();
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());

        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);
        support.assets.makePrivate(PublicCvTestConfiguration.EN_PDF_ID);
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());

        support.assets.reset();
        support.assets.removeContent(PublicCvTestConfiguration.EN_PDF_ID);
        mockMvc.perform(get("/cv/download")).andExpect(status().isNotFound());
    }
}

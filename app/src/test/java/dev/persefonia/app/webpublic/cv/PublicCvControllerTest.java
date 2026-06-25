package dev.persefonia.app.webpublic.cv;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.cv.CvDisplayLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class PublicCvControllerTest {
    private PublicCvMockMvcSupport support;
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        support = PublicCvMockMvcSupport.create();
        support.reset();
        mockMvc = support.mockMvc;
    }

    @Test
    void defaultLanguagePageRendersActiveCv() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                CvDisplayLabel.of("Public CV"),
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(containsString("<h1 id=\"cv-title\">CV</h1>")))
                .andExpect(content().string(containsString("Public CV")))
                .andExpect(content().string(containsString("cv-en.pdf")))
                .andExpect(content().string(containsString("href=\"/cv/en/download\"")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("/media/assets/"))))
                .andExpect(content().string(not(containsString("<iframe"))))
                .andExpect(content().string(not(containsString("<embed"))))
                .andExpect(content().string(not(containsString("<object"))))
                .andExpect(content().string(not(containsString("/admin"))));
    }

    @Test
    void cvPageRendersIndexableMetadataWithAbsoluteCanonicalAndNoFakeImage() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                CvDisplayLabel.of("Public CV"),
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv").header("Host", "evil.example"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<link rel=\"canonical\" href=\"https://example.test/cv\">")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"index, follow\">")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"Public CV\">")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"website\">")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:url\" content=\"https://example.test/cv\">")))
                .andExpect(content().string(containsString(
                        "<link rel=\"alternate\" type=\"application/atom+xml\"")))
                .andExpect(content().string(containsString("id=\"main-content\"")))
                .andExpect(content().string(not(containsString("og:image"))))
                .andExpect(content().string(not(containsString("twitter:image"))))
                .andExpect(content().string(not(containsString("evil.example"))));
    }

    @Test
    void explicitLanguageCvPageUsesLanguageScopedCanonical() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.TR,
                PublicCvTestConfiguration.TR_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv/tr"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<link rel=\"canonical\" href=\"https://example.test/cv/tr\">")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"index, follow\">")));
    }

    @Test
    void explicitLanguagePageRendersActiveCv() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.TR,
                PublicCvTestConfiguration.TR_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv/tr"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cv-tr.pdf")))
                .andExpect(content().string(containsString("href=\"/cv/tr/download\"")));
    }

    @Test
    void defaultLanguageDownloadStreamsPdfWithSafeHeaders() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cv-en.pdf\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, max-age=300, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))))
                .andExpect(header().exists("Content-Length"))
                .andExpect(content().bytes(("%PDF-" + PublicCvTestConfiguration.EN_PDF_ID.value()).getBytes()));
    }

    @Test
    void explicitLanguageDownloadStreamsPdf() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.TR,
                PublicCvTestConfiguration.TR_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv/tr/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cv-tr.pdf\""))
                .andExpect(content().bytes(("%PDF-" + PublicCvTestConfiguration.TR_PDF_ID.value()).getBytes()));
    }

    @Test
    void downloadRouteIsNotTreatedAsLanguageRoute() throws Exception {
        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);

        mockMvc.perform(get("/cv/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void unsupportedNoSelectionPrivateAndMissingContentCasesReturnNotFound() throws Exception {
        mockMvc.perform(get("/cv/de")).andExpect(status().isNotFound());
        mockMvc.perform(get("/cv/tr")).andExpect(status().isNotFound());

        support.profiles.profile().selectDocument(
                ContentLanguage.EN,
                PublicCvTestConfiguration.EN_PDF_ID,
                null,
                PublicCvTestConfiguration.NOW);
        support.assets.makePrivate(PublicCvTestConfiguration.EN_PDF_ID);
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());

        support.assets.reset();
        support.assets.removeContent(PublicCvTestConfiguration.EN_PDF_ID);
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/cv/download")).andExpect(status().isNotFound());
    }

    @Test
    void resumeAndGenericMediaPdfRoutesRemainAbsent() throws Exception {
        String assetId = PublicCvTestConfiguration.EN_PDF_ID.value().toString();

        mockMvc.perform(get("/resume")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/download", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/original", assetId)).andExpect(status().isNotFound());
    }
}

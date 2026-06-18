package dev.persefonia.app.webpublic.cv;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import dev.persefonia.webpublic.media.PublicMediaAssetController;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActiveCvPublicSurfaceTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("publicImageVariantContentService", mock(PublicImageVariantContentService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMediaAssetController(
                beans.getBeanProvider(PublicImageVariantContentService.class))).build();
    }

    @Test
    void cvAndResumeRoutesDoNotExist() throws Exception {
        mockMvc.perform(get("/cv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/resume")).andExpect(status().isNotFound());
    }

    @Test
    void publicPdfOriginalAndDownloadRoutesDoNotExist() throws Exception {
        String assetId = UUID.randomUUID().toString();

        mockMvc.perform(get("/media/assets/{assetId}", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/download", assetId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/assets/{assetId}/original", assetId)).andExpect(status().isNotFound());
    }
}

package dev.persefonia.app.webpublic.media;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContent;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import dev.persefonia.webpublic.media.PublicMediaAssetController;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicMediaAssetControllerTest {
    private PublicImageVariantContentService contentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        contentService = mock(PublicImageVariantContentService.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("publicImageVariantContentService", contentService);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMediaAssetController(
                beans.getBeanProvider(PublicImageVariantContentService.class))).build();
    }

    @Test
    void streamsEligibleVariantWithSafeHeaders() throws Exception {
        String assetId = UUID.randomUUID().toString();
        byte[] bytes = "variant".getBytes();
        when(contentService.openVariant(assetId, "thumbnail")).thenReturn(Optional.of(
                new PublicImageVariantContent(
                        new ByteArrayInputStream(bytes), "image/png", bytes.length)));

        mockMvc.perform(get("/media/assets/{assetId}/variants/{variantName}", assetId, "thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("variants/asset/"))));
    }

    @Test
    void ineligibleOrInvalidVariantReturnsNotFoundWithoutFallback() throws Exception {
        String assetId = UUID.randomUUID().toString();
        when(contentService.openVariant(assetId, "invalid")).thenReturn(Optional.empty());

        mockMvc.perform(get("/media/assets/{assetId}/variants/{variantName}", assetId, "invalid"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }
}

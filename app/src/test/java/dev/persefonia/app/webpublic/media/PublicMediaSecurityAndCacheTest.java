package dev.persefonia.app.webpublic.media;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContent;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import({
        TestPortfolioSettingsFallbackConfiguration.class,
        PublicMediaSecurityAndCacheTest.PublicMediaTestConfiguration.class
})
class PublicMediaSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;

    @Test
    void anonymousVariantResponseKeepsPublicCacheHeaders() throws Exception {
        mockMvc.perform(get(
                        "/media/assets/00000000-0000-0000-0000-000000000001/variants/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", "public, no-cache, must-revalidate"))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))))
                .andExpect(header().string("Cache-Control", not(containsString("private"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PublicMediaTestConfiguration {
        @Bean
        @Primary
        PublicImageVariantContentService publicImageVariantContentService() {
            PublicImageVariantContentService service = mock(PublicImageVariantContentService.class);
            byte[] bytes = "variant".getBytes();
            when(service.openVariant(
                    "00000000-0000-0000-0000-000000000001", "thumbnail"))
                    .thenReturn(Optional.of(new PublicImageVariantContent(
                            new ByteArrayInputStream(bytes), "image/png", bytes.length)));
            return service;
        }
    }
}

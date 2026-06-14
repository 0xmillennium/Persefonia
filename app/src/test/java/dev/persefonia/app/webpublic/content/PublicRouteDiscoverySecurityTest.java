package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicRouteDiscoverySecurityTest extends PublicContentMvcTestSupport {
    @Test
    void contentStateWithoutDiscoveryProjectionDoesNotRenderOrLeakSourceDetails() throws Exception {
        items.add(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "source-only"));

        mockMvc.perform(get("/tr/articles/source-only"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(not(containsString("Persisted HTML"))))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString("/admin/content"))));
    }

    @Test
    void discoveryProjectionForMissingContentDoesNotLeakSourceId() throws Exception {
        UUID missingSourceId = UUID.randomUUID();
        routes.addFound("/tr/articles/missing-source", missingSourceId);

        mockMvc.perform(get("/tr/articles/missing-source"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(missingSourceId.toString()))))
                .andExpect(content().string(not(containsString("source_entity_id"))));
    }
}

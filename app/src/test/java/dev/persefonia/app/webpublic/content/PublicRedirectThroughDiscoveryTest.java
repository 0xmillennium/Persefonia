package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import org.junit.jupiter.api.Test;

class PublicRedirectThroughDiscoveryTest extends PublicContentMvcTestSupport {
    @Test
    void redirectWinsWhenRedirectAndResourceConfiguredForSamePath() throws Exception {
        ContentItem item = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "path");
        items.add(item);
        routes.addFound("/tr/articles/path", item.id().value());
        routes.addRedirect(
                "/tr/articles/path",
                "/tr/articles/target",
                RedirectStatusCode.MOVED_PERMANENTLY_301);

        mockMvc.perform(get("/tr/articles/path"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/tr/articles/target"))
                .andExpect(content().string(not(containsString("Persisted HTML"))));
    }

    @Test
    void oldUnlistedSlugRedirectsWhenDiscoveryRedirectConfigured() throws Exception {
        routes.addRedirect(
                "/en/articles/old",
                "/en/articles/new",
                RedirectStatusCode.MOVED_PERMANENTLY_301);

        mockMvc.perform(get("/en/articles/old"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/en/articles/new"));
    }
}

package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentType;
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
@AutoConfigureMockMvc(addFilters = false)
@Import(PublicContentTestConfiguration.class)
@ActiveProfiles({"test", "public-content-mvc-test"})
class PublicContentIndexingTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        items.reset();
        routes.clear();
    }

    @Test
    void publicContentDoesNotRenderNoindex() throws Exception {
        addProjected(PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "indexable"), "/tr/articles/indexable");

        mockMvc.perform(get("/tr/articles/indexable"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<meta name=\"robots\" content=\"noindex\">"))));
    }

    @Test
    void unlistedContentRendersNoindex() throws Exception {
        addProjected(PublicContentTestItems.publishedUnlisted("unlisted-indexing"), "/tr/articles/unlisted-indexing");

        mockMvc.perform(get("/tr/articles/unlisted-indexing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));
    }

    @Test
    void notFoundContentRendersNoindex() throws Exception {
        mockMvc.perform(get("/tr/articles/missing-indexing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));
    }

    private void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }
}

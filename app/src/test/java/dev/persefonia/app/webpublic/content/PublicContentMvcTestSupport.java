package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import org.junit.jupiter.api.BeforeEach;
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
abstract class PublicContentMvcTestSupport {
    @Autowired MockMvc mockMvc;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void resetPublicContentMvcFakes() {
        items.reset();
        routes.clear();
    }

    void addProjected(ContentItem item, String publicPath) {
        items.add(item);
        routes.addFound(publicPath, item.id().value());
    }

    void assertRendered(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Public &lt;Title&gt;")))
                .andExpect(content().string(containsString("Summary &lt;strong&gt;escaped&lt;/strong&gt;")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev")))
                .andExpect(content().string(containsString("<strong>Persisted HTML</strong>")))
                .andExpect(content().string(containsString("4 min read")))
                .andExpect(content().string(containsString("Heading &lt;Escaped&gt;")))
                .andExpect(content().string(not(containsString("noindex"))))
                .andExpect(content().string(not(containsString("markdownSource"))))
                .andExpect(content().string(not(containsString("/admin/content"))))
                .andExpect(content().string(not(containsString("/preview"))))
                .andExpect(content().string(not(containsString("/revisions"))));
    }

    void assertSafeNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(containsString("noindex")))
                .andExpect(content().string(not(containsString("Persisted HTML"))))
                .andExpect(content().string(not(containsString("source_entity_id"))))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("draft"))))
                .andExpect(content().string(not(containsString("unpublished"))))
                .andExpect(content().string(not(containsString("archived"))))
                .andExpect(content().string(not(containsString("admin"))))
                .andExpect(content().string(not(containsString("preview"))))
                .andExpect(content().string(not(containsString("revision"))))
                .andExpect(content().string(not(containsString("exception"))))
                .andExpect(content().string(not(containsString("stack trace"))));
    }
}

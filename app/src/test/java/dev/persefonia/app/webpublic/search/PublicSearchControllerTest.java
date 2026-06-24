package dev.persefonia.app.webpublic.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.search.PublicSearchTestConfiguration.TrackingPublicSearchIndexQueryService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import({PublicContentTestConfiguration.class, PublicSearchTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-search-mvc-test"})
class PublicSearchControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired TrackingPublicSearchIndexQueryService searchIndex;

    @BeforeEach
    void reset() {
        searchIndex.reset();
    }

    @Test
    void searchFormIsPublicNoindexNoStoreAndDoesNotCallSearch() throws Exception {
        MvcResult result = mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(content().string(containsString("<form action=\"/search\" method=\"get\" role=\"search\">")))
                .andExpect(content().string(containsString("<label for=\"q\">Search query</label>")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, follow\">")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/search\">")))
                .andExpect(content().string(not(containsString("og:image"))))
                .andExpect(content().string(not(containsString("hreflang"))))
                .andExpect(cookie().doesNotExist("search"))
                .andExpect(cookie().doesNotExist("recentSearch"))
                .andReturn();

        assertThat(searchIndex.calls()).isZero();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void validSearchCallsQueryServiceAndRendersEscapedResults() throws Exception {
        searchIndex.returnResults();

        MvcResult result = mockMvc.perform(get("/search").param("q", "  portfolio  "))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(content().string(containsString("1 result for portfolio")))
                .andExpect(content().string(containsString("Portfolio &lt;Project&gt;")))
                .andExpect(content().string(containsString("A public &lt;summary&gt;")))
                .andExpect(content().string(containsString("<a href=\"/en/projects/portfolio\">")))
                .andExpect(content().string(containsString("Project")))
                .andExpect(content().string(containsString("English")))
                .andExpect(content().string(not(containsString("rank"))))
                .andExpect(content().string(not(containsString("debug SQL"))))
                .andExpect(content().string(not(containsString("iframe"))))
                .andExpect(content().string(not(containsString("embed"))))
                .andExpect(content().string(not(containsString("object"))))
                .andReturn();

        assertThat(searchIndex.calls()).isEqualTo(1);
        assertThat(searchIndex.lastRequest().query()).isEqualTo("portfolio");
        assertThat(searchIndex.lastRequest().limit()).isEqualTo(20);
        assertThat(searchIndex.lastRequest().offset()).isZero();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void blankAndInvalidQueriesDoNotCallQueryService() throws Exception {
        mockMvc.perform(get("/search").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("role=\"alert\""))));

        mockMvc.perform(get("/search").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter at least 2 characters to search.")));

        mockMvc.perform(get("/search").param("q", "x".repeat(121)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Search query is too long.")));

        mockMvc.perform(get("/search").param("q", "hello\u0000world"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Search query contains unsupported characters.")));

        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void invalidPageDoesNotCallQueryService() throws Exception {
        mockMvc.perform(get("/search").param("q", "portfolio").param("page", "invalid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page number is invalid.")));

        assertThat(searchIndex.calls()).isZero();
    }

    @Test
    void emptyResultStateRenders() throws Exception {
        searchIndex.returnEmptyPage();

        mockMvc.perform(get("/search").param("q", "portfolio"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0 results for portfolio")))
                .andExpect(content().string(containsString("No matching public results were found.")));

        assertThat(searchIndex.calls()).isEqualTo(1);
    }

    @Test
    void postAndWildcardSearchRoutesAreNotOpened() throws Exception {
        mockMvc.perform(post("/search"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/search/anything"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string("Cache-Control", not(Matchers.containsString("public"))));
    }
}

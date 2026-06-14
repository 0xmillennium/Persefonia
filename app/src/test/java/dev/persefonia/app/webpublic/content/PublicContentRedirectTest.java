package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import org.junit.jupiter.api.Test;

class PublicContentRedirectTest extends PublicContentMvcTestSupport {
    @Test
    void redirectDoesNotDependOnContentRepository() throws Exception {
        routes.addRedirect(
                "/tr/articles/old-slug",
                "/tr/articles/new-slug",
                RedirectStatusCode.MOVED_PERMANENTLY_301);

        mockMvc.perform(get("/tr/articles/old-slug"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/tr/articles/new-slug"))
                .andExpect(content().string(not(containsString("Persisted HTML"))));
    }
}

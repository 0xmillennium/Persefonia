package dev.persefonia.app.webpublic.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.contact.PublicContactTestConfiguration.TrackingPublicContactSubmissionGateway;
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
@AutoConfigureMockMvc
@Import(PublicContactTestConfiguration.class)
@ActiveProfiles({"test", "public-contact-mvc-test"})
class PublicContactControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired TrackingPublicContactSubmissionGateway submissions;

    @BeforeEach
    void reset() {
        submissions.reset();
    }

    @Test
    void getContactRendersPublicNoindexNoStoreForm() throws Exception {
        mockMvc.perform(get("/contact"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("id=\"main-content\"")))
                .andExpect(content().string(containsString("<h1>Contact</h1>")))
                .andExpect(content().string(containsString("<form method=\"post\" action=\"/contact\">")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("<label for=\"senderName\">Name</label>")))
                .andExpect(content().string(containsString("<label for=\"senderEmail\">Email</label>")))
                .andExpect(content().string(containsString("<label for=\"subject\">Subject</label>")))
                .andExpect(content().string(containsString("<label for=\"body\">Message</label>")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, follow\">")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://0xmillennium.dev/contact\">")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"https://0xmillennium.dev/contact\">")))
                .andExpect(content().string(not(containsString("og:image"))))
                .andExpect(content().string(not(containsString("twitter:image"))))
                .andExpect(content().string(not(containsString("href=\"\""))))
                .andExpect(content().string(not(containsString("<iframe"))))
                .andExpect(content().string(not(containsString("<embed"))))
                .andExpect(content().string(not(containsString("<object"))));

        assertThat(submissions.calls()).isZero();
    }

    @Test
    void submittedFlagRendersSafeSuccessMessage() throws Exception {
        mockMvc.perform(get("/contact").param("submitted", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your message was received.")))
                .andExpect(content().string(not(containsString("email was sent"))));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(validPost())
                .andExpect(status().isForbidden());

        assertThat(submissions.calls()).isZero();
    }

    @Test
    void validPostWithCsrfRedirectsAfterSubmission() throws Exception {
        mockMvc.perform(validPost().with(csrf()).with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    return request;
                }))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(redirectedUrl("/contact?submitted=1"));

        assertThat(submissions.calls()).isEqualTo(1);
        assertThat(submissions.lastRequest().senderName()).isEqualTo("Ada");
        assertThat(submissions.lastRequest().senderEmail()).isEqualTo("ada@example.test");
        assertThat(submissions.lastRequest().subject()).isEqualTo("Hello");
        assertThat(submissions.lastRequest().body()).isEqualTo("Body");
        assertThat(submissions.lastRequest().transientClientSignal()).isEqualTo("203.0.113.10");
    }

    @Test
    void invalidPostRendersFieldErrorsAndEscapesEchoedValues() throws Exception {
        submissions.invalid();

        mockMvc.perform(post("/contact")
                        .with(csrf())
                        .param("senderName", "<Ada>")
                        .param("senderEmail", "invalid")
                        .param("subject", "<Hello>")
                        .param("body", "<Body>"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Please correct the highlighted fields.")))
                .andExpect(content().string(containsString("value=\"&lt;Ada>\"")))
                .andExpect(content().string(containsString("value=\"&lt;Hello>\"")))
                .andExpect(content().string(containsString("&lt;Body&gt;")))
                .andExpect(content().string(containsString("aria-describedby=\"senderEmail-error\"")))
                .andExpect(content().string(containsString("aria-describedby=\"body-error\"")));

        assertThat(submissions.calls()).isEqualTo(1);
    }

    @Test
    void cheapOversizedPostDoesNotCallSubmissionGateway() throws Exception {
        mockMvc.perform(validPost().with(csrf()).param("body", "a".repeat(6001)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This field is too long.")));

        assertThat(submissions.calls()).isZero();
    }

    @Test
    void rateLimitExceededReturnsFriendly429AndPersistsNothingThroughGateway() throws Exception {
        submissions.rateLimited();

        mockMvc.perform(validPost().with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Please wait before sending another message.")))
                .andExpect(content().string(not(containsString("rate-limit"))));

        assertThat(submissions.calls()).isEqualTo(1);
    }

    @Test
    void unavailableRateLimitReturnsFriendly503AndPersistsNothingThroughGateway() throws Exception {
        submissions.temporarilyUnavailable();

        mockMvc.perform(validPost().with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("temporarily unavailable")))
                .andExpect(content().string(not(containsString("Redis"))));

        assertThat(submissions.calls()).isEqualTo(1);
    }

    @Test
    void contactWildcardsAndApiOrAdminRoutesAreNotOpened() throws Exception {
        mockMvc.perform(get("/contact/anything")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/contact").with(csrf())).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/contact")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/insights")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/analytics")).andExpect(status().is4xxClientError());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/contact")
                .param("senderName", "Ada")
                .param("senderEmail", "ada@example.test")
                .param("subject", "Hello")
                .param("body", "Body");
    }
}

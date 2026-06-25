package dev.persefonia.app.webadmin.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.contact.AdminContactTestConfiguration.ContactMessageStore;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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
@Import(AdminContactTestConfiguration.class)
@ActiveProfiles({"test", "admin-contact-mvc-test"})
class AdminContactControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ContactMessageStore store;

    @BeforeEach
    void reset() {
        store.reset();
        store.save(AdminContactTestConfiguration.withMailAttempt(AdminContactTestConfiguration.message()));
        store.save(AdminContactTestConfiguration.secondMessage());
    }

    @Test
    void listRouteReturnsAdminContactPageWithoutFullBody() throws Exception {
        mockMvc.perform(get("/admin/contact").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("<h1>Contact inbox</h1>")))
                .andExpect(content().string(containsString("Ada Lovelace")))
                .andExpect(content().string(containsString("ada@example.test")))
                .andExpect(content().string(containsString("Hello from the contact form")))
                .andExpect(content().string(containsString("FAILED")))
                .andExpect(content().string(containsString("href=\"/admin/contact/"
                        + AdminContactTestConfiguration.MESSAGE_ID.value() + "\"")))
                .andExpect(content().string(not(containsString("Private &lt;b&gt;body&lt;/b&gt;"))))
                .andExpect(content().string(not(containsString("Another private body"))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/reply\""))))
                .andExpect(content().string(not(containsString("/resend"))))
                .andExpect(content().string(not(containsString("/bulk"))))
                .andExpect(content().string(not(containsString("/export"))));
    }

    @Test
    void listRouteSupportsTypedStatusFilterAndEmptyState() throws Exception {
        mockMvc.perform(get("/admin/contact").param("status", "SPAM").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No contact messages yet.")))
                .andExpect(content().string(containsString("value=\"SPAM\" selected")));
    }

    @Test
    void detailRouteReturnsDetailPageWithEscapedBodyMailAttemptsAndStatusChanges() throws Exception {
        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/read")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Hello from the contact form</h1>")))
                .andExpect(content().string(containsString("Private &lt;b&gt;body&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("Private <b>body</b>"))))
                .andExpect(content().string(containsString("Mail attempts")))
                .andExpect(content().string(containsString("SMTP unavailable")))
                .andExpect(content().string(containsString("Status changes")))
                .andExpect(content().string(containsString("READ")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Mark as replied")))
                .andExpect(content().string(containsString("Mark as spam")))
                .andExpect(content().string(containsString("Archive")))
                .andExpect(content().string(not(containsString("Mark as read"))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/reply\""))))
                .andExpect(content().string(not(containsString("/resend"))))
                .andExpect(content().string(not(containsString("/bulk"))))
                .andExpect(content().string(not(containsString("/export"))))
                .andExpect(content().string(not(containsString("href=\"\""))));
    }

    @Test
    void statusPostsRedirectAfterSuccessAndPersistStatus() throws Exception {
        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/replied")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "?updated"));

        assertThat(store.findById(AdminContactTestConfiguration.MESSAGE_ID).orElseThrow().status())
                .isEqualTo(ContactMessageStatus.REPLIED);
        assertThat(store.findById(AdminContactTestConfiguration.MESSAGE_ID).orElseThrow().statusChanges())
                .hasSize(1);
        assertThat(store.findById(AdminContactTestConfiguration.MESSAGE_ID).orElseThrow().mailNotificationAttempts())
                .hasSize(1);
    }

    @Test
    void invalidAndMissingMessageIdsAreHandledSafely() throws Exception {
        mockMvc.perform(get("/admin/contact/not-a-uuid").with(owner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/contact/" + AdminContactTestConfiguration.MISSING_ID.value()).with(owner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/contact/not-a-uuid/read").with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MISSING_ID.value() + "/read")
                        .with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void forbiddenContactWorkflowRoutesDoNotExist() throws Exception {
        String base = "/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value();

        mockMvc.perform(post(base + "/delete").with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(base + "/reply").with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(base + "/resend").with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/contact/bulk").with(owner()).with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(get("/admin/contact/export").with(owner()))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}

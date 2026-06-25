package dev.persefonia.app.webadmin.contact;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.contact.AdminContactTestConfiguration.ContactMessageStore;
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
@ActiveProfiles({"test", "admin-contact-template-test"})
class AdminContactTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired ContactMessageStore store;

    @BeforeEach
    void reset() {
        store.reset();
    }

    @Test
    void listTemplateRendersRowsFiltersEmptyStateAndDetailLinks() throws Exception {
        mockMvc.perform(get("/admin/contact").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No contact messages yet.")))
                .andExpect(content().string(containsString("name=\"status\"")))
                .andExpect(content().string(not(containsString("href=\"\""))));

        store.save(AdminContactTestConfiguration.message());

        mockMvc.perform(get("/admin/contact").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ada Lovelace")))
                .andExpect(content().string(containsString("Hello from the contact form")))
                .andExpect(content().string(containsString("href=\"/admin/contact/"
                        + AdminContactTestConfiguration.MESSAGE_ID.value() + "\"")))
                .andExpect(content().string(not(containsString("Private &lt;b&gt;body&lt;/b&gt;"))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/reply\""))))
                .andExpect(content().string(not(containsString("/resend"))))
                .andExpect(content().string(not(containsString("/bulk"))))
                .andExpect(content().string(not(containsString("/export"))));
    }

    @Test
    void detailTemplateEscapesBodyAndRendersCsrfStatusFormsHistoryAndAttempts() throws Exception {
        store.save(AdminContactTestConfiguration.withMailAttempt(AdminContactTestConfiguration.message()));

        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/read")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Private &lt;b&gt;body&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("Private <b>body</b>"))))
                .andExpect(content().string(containsString("Mail attempts")))
                .andExpect(content().string(containsString("Status changes")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Mark as replied")))
                .andExpect(content().string(containsString("Mark as spam")))
                .andExpect(content().string(containsString("Archive")))
                .andExpect(content().string(not(containsString("Mark as read"))))
                .andExpect(content().string(not(containsString("href=\"\""))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/reply\""))))
                .andExpect(content().string(not(containsString("/resend"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}

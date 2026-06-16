package dev.persefonia.app.webadmin.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.settings.AdminSiteSettingsTestConfiguration.AdminSiteSettingsTestRepository;
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
@Import(AdminSiteSettingsTestConfiguration.class)
@ActiveProfiles({"test", "admin-site-settings-mvc-test"})
class AdminSiteSettingsSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminSiteSettingsTestRepository settings;

    @BeforeEach
    void reset() {
        settings.reset();
    }

    @Test
    void anonymousGetIsRejectedAndOwnerGetHasSensitiveCacheHeaders() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/settings/site")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/settings/site").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void postRequiresCsrfAndNonOwnerCannotSubmitStateChangingUpdate() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));

        mockMvc.perform(validPost().with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(validPost().with(editor).with(csrf())).andExpect(status().isForbidden());

        assertThat(settings.current().siteName().value()).isEqualTo("Seeded Site");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/admin/settings/site")
                .param("siteName", "Updated Site")
                .param("defaultLanguage", "EN")
                .param("supportedTr", "true")
                .param("supportedEn", "true")
                .param("defaultTheme", "DARK")
                .param("featuredProjectLimit", "4")
                .param("latestWritingLimit", "6");
    }
}

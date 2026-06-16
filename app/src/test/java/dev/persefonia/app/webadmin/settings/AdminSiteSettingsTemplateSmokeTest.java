package dev.persefonia.app.webadmin.settings;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminSiteSettingsTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminSiteSettingsTestRepository settings;

    @BeforeEach
    void reset() {
        settings.reset();
    }

    @Test
    void settingsTemplateRendersPageAndDoesNotExposeAggregateOrOgAssetField() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/settings/site").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<form method=\"post\" action=\"/admin/settings/site\">")))
                .andExpect(content().string(containsString("Site settings")))
                .andExpect(content().string(not(containsString("SitePresentationSettings"))))
                .andExpect(content().string(not(containsString("defaultOgImageAssetId"))))
                .andExpect(content().string(not(containsString("defaultOpenGraphImageAssetId"))));
    }

    @Test
    void settingsTemplateRendersFieldAndGlobalErrors() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/settings/site").with(owner).with(csrf())
                        .param("siteName", " ")
                        .param("defaultLanguage", "TR")
                        .param("defaultTheme", "SYSTEM")
                        .param("featuredProjectLimit", "abc")
                        .param("latestWritingLimit", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Site name is required.")))
                .andExpect(content().string(containsString("Featured project limit must be a whole number.")));
    }
}

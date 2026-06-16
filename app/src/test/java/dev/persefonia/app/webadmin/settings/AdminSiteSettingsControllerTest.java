package dev.persefonia.app.webadmin.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.settings.AdminSiteSettingsTestConfiguration.AdminSiteSettingsTestRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.settings.ThemePreference;
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
class AdminSiteSettingsControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminSiteSettingsTestRepository settings;

    @BeforeEach
    void reset() {
        settings.reset();
    }

    @Test
    void ownerCanViewSettingsPageWithSeededValuesAndCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/settings/site").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Site settings")))
                .andExpect(content().string(containsString("Seeded Site")))
                .andExpect(content().string(containsString("Seeded meta description.")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Settings")))
                .andExpect(content().string(not(containsString("defaultOgImageAssetId"))))
                .andExpect(content().string(not(containsString("defaultOpenGraphImageAssetId"))));
    }

    @Test
    void ownerCanSubmitValidSettingsUpdateAndSettingsPersist() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(validPost().with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings/site?saved"));

        assertThat(settings.current().siteName().value()).isEqualTo("Updated Site");
        assertThat(settings.current().defaultLanguage()).isEqualTo(ContentLanguage.EN);
        assertThat(settings.current().defaultTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(settings.current().homepageSettings().featuredProjectLimit().value()).isEqualTo(4);
        assertThat(settings.current().homepageSettings().latestWritingLimit().value()).isEqualTo(6);
    }

    @Test
    void invalidInputsReturnFriendlyFieldErrors() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/settings/site").with(owner).with(csrf())
                        .param("siteName", " ")
                        .param("defaultLanguage", "DE")
                        .param("defaultTheme", "BLUE")
                        .param("featuredProjectLimit", "0")
                        .param("latestWritingLimit", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Site name is required.")))
                .andExpect(content().string(containsString("Choose TR or EN.")))
                .andExpect(content().string(containsString("Select at least one supported language.")))
                .andExpect(content().string(containsString("Choose LIGHT, DARK, or SYSTEM.")))
                .andExpect(content().string(containsString("Featured project limit must be positive.")))
                .andExpect(content().string(containsString("Latest writing limit must be a whole number.")));

        mockMvc.perform(post("/admin/settings/site").with(owner).with(csrf())
                        .param("siteName", "Updated Site")
                        .param("defaultLanguage", "EN")
                        .param("supportedTr", "true")
                        .param("defaultTheme", "SYSTEM")
                        .param("featuredProjectLimit", "3")
                        .param("latestWritingLimit", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Default language must be one of the supported languages.")));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/admin/settings/site")
                .param("siteName", "Updated Site")
                .param("defaultLanguage", "EN")
                .param("supportedTr", "true")
                .param("supportedEn", "true")
                .param("titleSuffix", "| Updated")
                .param("defaultMetaDescription", "Updated description.")
                .param("defaultTheme", "DARK")
                .param("showFeaturedProjects", "true")
                .param("showLatestWriting", "true")
                .param("featuredProjectLimit", "4")
                .param("latestWritingLimit", "6");
    }
}

package dev.persefonia.app.webadmin.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.profile.AdminPersonalProfileTestConfiguration.AdminPersonalProfileTestRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
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
@Import(AdminPersonalProfileTestConfiguration.class)
@ActiveProfiles({"test", "admin-personal-profile-mvc-test"})
class AdminPersonalProfileControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminPersonalProfileTestRepository profiles;

    @BeforeEach
    void reset() {
        profiles.reset();
    }

    @Test
    void getWithNoProfileRendersOnboardingFormWithCsrfAndNavigation() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/profile").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create profile")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("href=\"/admin/profile\" aria-current=\"page\"")))
                .andExpect(content().string(containsString("href=\"/admin/settings/site\"")))
                .andExpect(content().string(containsString("href=\"/admin/projects\"")));
    }

    @Test
    void ownerCanCreateAndEditActiveProfile() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(validPost("Enes").with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/profile?saved"));

        assertThat(profiles.current().displayName().value()).isEqualTo("Enes");
        assertThat(profiles.current().hasLocalization(ContentLanguage.TR)).isTrue();

        mockMvc.perform(get("/admin/profile").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit profile")))
                .andExpect(content().string(containsString("Enes")))
                .andExpect(content().string(containsString("TR short bio")));

        mockMvc.perform(validPost("Updated").with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/profile?saved"));

        assertThat(profiles.current().displayName().value()).isEqualTo("Updated");
        assertThat(profiles.current().version().value()).isEqualTo(1);
    }

    @Test
    void invalidInputsReturnFriendlyErrorsAndPreserveForm() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/profile").with(owner).with(csrf())
                        .param("displayName", " ")
                        .param("enEnabled", "true")
                        .param("enShortBio", "Short")
                        .param("enLongBio", "Long")
                        .param("enEducationSummaries", "Broken")
                        .param("enTechnicalFocusAreas", "Bad | Too | Many")
                        .param("externalLinks", "Bad line\nSite | ftp://example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Display name is required.")))
                .andExpect(content().string(containsString("Enable the settings default-language localization (TR).")))
                .andExpect(content().string(containsString("External links line 1")))
                .andExpect(content().string(containsString("External links line 2 must use a valid http or https URL.")))
                .andExpect(content().string(containsString("Education summaries line 1")))
                .andExpect(content().string(containsString("Technical focus areas line 1")))
                .andExpect(content().string(containsString("Bad line")));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost(String displayName) {
        return post("/admin/profile")
                .param("displayName", displayName)
                .param("trEnabled", "true")
                .param("trShortBio", "TR short bio")
                .param("trLongBio", "TR long bio")
                .param("trLocationText", "Istanbul")
                .param("trTechnicalFocusAreas", "Architecture | Systems")
                .param("trEducationSummaries", "University | Computer Science")
                .param("trCurrentFocusItems", "Building")
                .param("externalLinks", "Website | https://example.test");
    }
}

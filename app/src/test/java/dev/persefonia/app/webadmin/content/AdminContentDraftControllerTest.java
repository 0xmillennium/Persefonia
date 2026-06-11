package dev.persefonia.app.webadmin.content;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
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
@Import(AdminContentTestConfiguration.class)
@ActiveProfiles({"test", "admin-content-mvc-test"})
class AdminContentDraftControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void newFormRendersWithCsrf() throws Exception {
        mockMvc.perform(get("/admin/content/new")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create draft")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void validCreateUsesApplicationServiceAndRedirectsToEdit() throws Exception {
        mockMvc.perform(post("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())
                        .param("type", "ARTICLE")
                        .param("language", "EN")
                        .param("visibility", "PRIVATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/content/*/edit?created"));
        org.assertj.core.api.Assertions.assertThat(items.saveCount()).isEqualTo(1);
    }

    @Test
    void createRequiresCsrfAndOwner() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/content").with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                        .with(csrf())
                        .param("type", "ARTICLE").param("language", "EN").param("visibility", "PRIVATE"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(items.saveCount()).isZero();
    }

    @Test
    void invalidCreateRerendersFieldErrorsAndSubmittedValue() throws Exception {
        mockMvc.perform(post("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())
                        .param("type", "ARTICLE")
                        .param("language", "")
                        .param("visibility", "PRIVATE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("language: This field is required.")))
                .andExpect(content().string(containsString("value=\"ARTICLE\" selected")));
    }
}

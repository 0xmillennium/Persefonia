package dev.persefonia.app.webadmin.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.profile.AdminPersonalProfileTestConfiguration.AdminPersonalProfileTestRepository;
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
@Import(AdminPersonalProfileTestConfiguration.class)
@ActiveProfiles({"test", "admin-personal-profile-mvc-test"})
class AdminPersonalProfileSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminPersonalProfileTestRepository profiles;

    @BeforeEach
    void reset() {
        profiles.reset();
    }

    @Test
    void getRequiresAdminAndHasSensitiveCacheHeaders() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/profile")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/profile").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void postRequiresCsrfAndOwnerAuthorizationInApplicationLayer() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));

        mockMvc.perform(validPost().with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(validPost().with(editor).with(csrf())).andExpect(status().isForbidden());

        assertThat(profiles.current()).isNull();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/admin/profile")
                .param("displayName", "Enes")
                .param("trEnabled", "true")
                .param("trShortBio", "TR short bio")
                .param("trLongBio", "TR long bio");
    }
}

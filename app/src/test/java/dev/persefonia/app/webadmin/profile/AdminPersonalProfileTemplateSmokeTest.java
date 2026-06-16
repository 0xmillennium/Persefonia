package dev.persefonia.app.webadmin.profile;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminPersonalProfileTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminPersonalProfileTestRepository profiles;

    @BeforeEach
    void reset() {
        profiles.reset();
    }

    @Test
    void profileTemplateRendersFormAndDoesNotExposeAggregateOrOutOfScopeFields() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/profile").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<form method=\"post\" action=\"/admin/profile\">")))
                .andExpect(content().string(containsString("Display name")))
                .andExpect(content().string(containsString("External links")))
                .andExpect(content().string(not(containsString("PersonalProfile "))))
                .andExpect(content().string(not(containsString("cvDownload"))))
                .andExpect(content().string(not(containsString("avatarAssetId"))))
                .andExpect(content().string(not(containsString("projectIds"))))
                .andExpect(content().string(not(containsString("/admin/projects"))));
    }
}

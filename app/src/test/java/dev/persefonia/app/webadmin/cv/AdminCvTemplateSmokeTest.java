package dev.persefonia.app.webadmin.cv;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.cv.AdminCvTestConfiguration.AdminCvEligibilityStub;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
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
@Import(AdminCvTestConfiguration.class)
@ActiveProfiles({"test", "admin-cv-mvc-test"})
class AdminCvTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminCvEligibilityStub eligibility;

    @Test
    void templateRendersCandidatePdfsSafely() throws Exception {
        mockMvc.perform(get("/admin/cv").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cv-en.pdf")))
                .andExpect(content().string(not(containsString("storage_path"))))
                .andExpect(content().string(not(containsString("/media/assets/"))))
                .andExpect(content().string(not(containsString("href=\"/cv\""))))
                .andExpect(content().string(not(containsString("href=\"/resume\""))));
    }

    @Test
    void templateRendersEmptyStateWithAdminMediaLink() throws Exception {
        eligibility.clear();

        mockMvc.perform(get("/admin/cv").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public PDF assets are available.")))
                .andExpect(content().string(containsString("href=\"/admin/media\"")));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}

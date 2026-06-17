package dev.persefonia.app.webadmin.projects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.projects.AdminProjectTestConfiguration.AdminProjectTestRepository;
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
@Import(AdminProjectTestConfiguration.class)
@ActiveProfiles({"test", "admin-project-mvc-test"})
class AdminProjectTemplateSmokeTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminProjectTestRepository projects;

    @BeforeEach
    void reset() {
        projects.reset();
    }

    @Test
    void projectTemplatesRenderAllowedAdminFormsAndDoNotExposeOutOfScopeRoutesOrAssets() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/projects/new").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<form method=\"post\" action=\"/admin/projects\">")))
                .andExpect(content().string(containsString("Problem")))
                .andExpect(content().string(containsString("value=\"EXPERIMENT\" selected")))
                .andExpect(content().string(not(containsString("coverAssetId"))))
                .andExpect(content().string(not(containsString("/publish"))))
                .andExpect(content().string(not(containsString("/archive"))))
                .andExpect(content().string(not(containsString("/delete"))))
                .andExpect(content().string(not(containsString("/preview"))));

        mockMvc.perform(post("/admin/projects").with(owner).with(csrf())
                        .param("status", "EXPERIMENT")
                        .param("visibility", "PRIVATE")
                        .param("trEnabled", "true")
                        .param("trSlug", "sample-project")
                        .param("trTitle", "Sample Project")
                        .param("trSummary", "Summary")
                        .param("trProblem", "Problem body"))
                .andExpect(status().is3xxRedirection());

        var project = projects.all().getFirst();
        mockMvc.perform(get("/admin/projects/" + project.id().value() + "/edit").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sample Project")))
                .andExpect(content().string(containsString("Problem body")))
                .andExpect(content().string(not(containsString("coverAssetId"))));
    }
}

package dev.persefonia.app.webadmin.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.projects.AdminProjectTestConfiguration.AdminProjectTagVocabulary;
import dev.persefonia.app.webadmin.projects.AdminProjectTestConfiguration.AdminProjectTestRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
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
class AdminProjectControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminProjectTestRepository projects;
    @Autowired AdminProjectTagVocabulary tags;

    @BeforeEach
    void reset() {
        projects.reset();
        tags.reset();
    }

    @Test
    void ownerCanViewListAndNewFormWithSensitiveHeadersAndCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/projects").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")));

        mockMvc.perform(get("/admin/projects/new").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create project")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Java")));
    }

    @Test
    void ownerCanCreateEditAndUpdateProject() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(validCreate().with(owner).with(csrf())
                        .param("tagIds", tags.activeTagId().value().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/projects/*/edit?created"));

        var project = projects.all().getFirst();
        assertThat(project.visibility()).isEqualTo(ProjectVisibility.PUBLIC);
        assertThat(project.tagIds()).containsExactly(tags.activeTagId());

        mockMvc.perform(get("/admin/projects/" + project.id().value() + "/edit").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit project")))
                .andExpect(content().string(containsString("Project TR")));

        mockMvc.perform(post("/admin/projects/" + project.id().value()).with(owner).with(csrf())
                        .param("status", "COMPLETED")
                        .param("visibility", "PRIVATE")
                        .param("trEnabled", "true")
                        .param("trSlug", "project-tr")
                        .param("trTitle", "Project TR")
                        .param("trSummary", "Updated summary"))
                .andExpect(status().is3xxRedirection());

        assertThat(projects.all().getFirst().status()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(projects.all().getFirst().visibility()).isEqualTo(ProjectVisibility.PRIVATE);
    }

    @Test
    void anonymousAndNonOwnerCannotMutateAndPostsRequireCsrf() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/projects")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/admin/projects").with(editor).with(csrf())
                        .param("status", "ACTIVE").param("visibility", "PRIVATE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(validCreate().with(owner))
                .andExpect(status().isForbidden());
        assertThat(projects.all()).isEmpty();
    }

    @Test
    void invalidFeaturedProjectShowsFriendlyError() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/projects").with(owner).with(csrf())
                        .param("status", "ARCHIVED")
                        .param("visibility", "PUBLIC")
                        .param("featured", "true")
                        .param("trEnabled", "true")
                        .param("trSlug", "project-tr")
                        .param("trTitle", "Project TR")
                        .param("trSummary", "Summary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Archived projects cannot be featured.")));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validCreate() {
        return post("/admin/projects")
                .param("status", "ACTIVE")
                .param("visibility", "PUBLIC")
                .param("trEnabled", "true")
                .param("trSlug", "project-tr")
                .param("trTitle", "Project TR")
                .param("trSummary", "Summary")
                .param("technologies", "Java | LANGUAGE")
                .param("links", "Source | https://example.test | SOURCE");
    }
}

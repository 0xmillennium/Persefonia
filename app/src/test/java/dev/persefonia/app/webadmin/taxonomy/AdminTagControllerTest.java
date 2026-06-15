package dev.persefonia.app.webadmin.taxonomy;

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
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.taxonomy.domain.model.TagStatus;
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
@Import(AdminTagTestConfiguration.class)
@ActiveProfiles({"test", "admin-tag-mvc-test"})
class AdminTagControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminTagTestRepository tags;

    @BeforeEach
    void reset() {
        tags.reset();
    }

    @Test
    void ownerCanViewListAndNewFormWithSensitiveHeadersAndCsrf() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(get("/admin/tags").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")));
        mockMvc.perform(get("/admin/tags/new").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create tag")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void ownerCanCreateEditUpdateAndArchiveTag() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/tags").with(owner).with(csrf())
                        .param("name", "İçerik").param("slug", "").param("description", "Taxonomy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/tags/*/edit?created"));

        var tag = tags.all().getFirst();
        mockMvc.perform(get("/admin/tags/" + tag.id().value() + "/edit").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit tag")));
        mockMvc.perform(post("/admin/tags/" + tag.id().value()).with(owner).with(csrf())
                        .param("name", "Content").param("slug", "content").param("description", "Updated"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/tags/" + tag.id().value() + "/archive").with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(tags.all().getFirst().status()).isEqualTo(TagStatus.ARCHIVED);
    }

    @Test
    void anonymousAndNonOwnerCannotMutateAndPostsRequireCsrf() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/tags")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/tags").with(editor)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/tags").with(editor).with(csrf()).param("name", "Java"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/tags").with(owner).param("name", "Java"))
                .andExpect(status().isForbidden());
        assertThat(tags.all()).isEmpty();
    }

    @Test
    void duplicateAndInvalidInputShowFriendlyFieldErrors() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/tags").with(owner).with(csrf()).param("name", "Java").param("slug", "java"));
        mockMvc.perform(post("/admin/tags").with(owner).with(csrf()).param("name", "JVM").param("slug", "java"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This slug is already in use.")));
        mockMvc.perform(post("/admin/tags").with(owner).with(csrf()).param("name", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This field is required.")));
    }
}

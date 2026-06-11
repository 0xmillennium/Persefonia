package dev.persefonia.app.webadmin.content;

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
class AdminContentEditControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void editRendersDraftAndUnpublishedWithoutLifecycleControls() throws Exception {
        for (var item : java.util.List.of(AdminContentTestFixtures.completeDraft(), AdminContentTestFixtures.unpublished())) {
            items.add(item);
            mockMvc.perform(get("/admin/content/" + item.id().value() + "/edit")
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(item.status().name())))
                    .andExpect(content().string(containsString("Admin draft")))
                    .andExpect(content().string(not(containsString(">Publish<"))))
                    .andExpect(content().string(not(containsString(">Unpublish<"))))
                    .andExpect(content().string(not(containsString(">Archive<"))));
        }
    }

    @Test
    void validDraftAndUnpublishedUpdatesRedirectAndRequireCsrf() throws Exception {
        for (var item : java.util.List.of(AdminContentTestFixtures.completeDraft(), AdminContentTestFixtures.unpublished())) {
            items.add(item);
            String path = "/admin/content/" + item.id().value();
            mockMvc.perform(post(path)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                            .with(csrf())
                            .params(validUpdate()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(path + "/edit?saved"));
            mockMvc.perform(post(path)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                            .params(validUpdate()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void archivedAndPublishedDirectEditsReturnSafeError() throws Exception {
        for (var item : java.util.List.of(AdminContentTestFixtures.archived(), published())) {
            items.add(item);
            mockMvc.perform(post("/admin/content/" + item.id().value())
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                            .with(csrf())
                            .params(validUpdate()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("could not be updated")))
                    .andExpect(content().string(not(containsString("Exception"))));
        }
    }

    @Test
    void archivedAndPublishedEditPagesShowSafeNonEditableError() throws Exception {
        for (var item : java.util.List.of(AdminContentTestFixtures.archived(), published())) {
            items.add(item);
            mockMvc.perform(get("/admin/content/" + item.id().value() + "/edit")
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Only draft or unpublished content can be edited.")))
                    .andExpect(content().string(not(containsString("Exception"))));
        }
    }

    @Test
    void validationPreservesAndEscapesSubmittedMarkdown() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        mockMvc.perform(post("/admin/content/" + item.id().value())
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                        .with(csrf())
                        .params(validUpdate())
                        .param("slug", "INVALID")
                        .param("markdownSource", "</textarea><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Use lowercase letters")))
                .andExpect(content().string(containsString("&lt;/textarea&gt;&lt;script&gt;")))
                .andExpect(content().string(not(containsString("</textarea><script>"))));
    }

    @Test
    void missingEditIsNotFoundAndEditorCannotUpdate() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        mockMvc.perform(get("/admin/content/" + id + "/edit")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/content/" + id)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                        .with(csrf()).params(validUpdate()))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.util.LinkedMultiValueMap<String, String> validUpdate() {
        var values = new org.springframework.util.LinkedMultiValueMap<String, String>();
        values.add("type", "ARTICLE");
        values.add("language", "EN");
        values.add("visibility", "PRIVATE");
        values.add("slug", "updated-admin-draft");
        values.add("title", "Updated admin draft");
        values.add("summary", "Updated summary");
        values.add("markdownSource", "# Updated");
        values.add("canonicalPath", "/articles/updated-admin-draft");
        return values;
    }

    private static dev.persefonia.contentpublishing.domain.content.ContentItem published() {
        var item = AdminContentTestFixtures.unpublished();
        item.publish(dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot.of(
                dev.persefonia.contentpublishing.domain.content.RenderedHtml.sanitized("<p>again</p>"),
                AdminContentTestFixtures.CREATED.plusSeconds(5),
                dev.persefonia.contentpublishing.domain.content.RendererVersion.of("test"),
                dev.persefonia.contentpublishing.domain.content.ReadingTime.minutes(1),
                false,
                java.util.List.of()), AdminContentTestFixtures.CREATED.plusSeconds(5));
        return item;
    }
}

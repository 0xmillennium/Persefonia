package dev.persefonia.app.webadmin.content;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminContentRevisionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;
    @Autowired AdminContentTestRevisionRepository revisions;

    @BeforeEach
    void reset() {
        items.reset();
        revisions.reset();
    }

    @Test
    void ownerCanViewEmptyRevisionHistory() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);

        mockMvc.perform(get(path(item)).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin draft")))
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(containsString("No revisions have been created yet.")));
    }

    @Test
    void revisionRowsRenderNewestFirstWithSafeSnapshotFields() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        revisions.save(AdminContentTestFixtures.revision(item, 1, "First revision"));
        revisions.save(AdminContentTestFixtures.revision(item, 2, "Second revision"));

        String body = mockMvc.perform(get(path(item)).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PUBLISH")))
                .andExpect(content().string(containsString("revision-2")))
                .andExpect(content().string(containsString("Change 2")))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body.indexOf("Second revision"))
                .isLessThan(body.indexOf("First revision"));
    }

    @Test
    void missingOrMalformedContentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/admin/content/" + java.util.UUID.randomUUID() + "/revisions").with(owner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/content/not-an-id/revisions").with(owner()))
                .andExpect(status().isNotFound());
    }

    private static String path(dev.persefonia.contentpublishing.domain.content.ContentItem item) {
        return "/admin/content/" + item.id().value() + "/revisions";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
}

package dev.persefonia.app.webadmin.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AdminContentListControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void ownerSeesEmptyStateAndNoStoreHeaders() throws Exception {
        mockMvc.perform(get("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No editable content yet.")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void listRendersDraftAndUnpublishedWithoutPublicLinks() throws Exception {
        items.add(AdminContentTestFixtures.completeDraft());
        items.add(AdminContentTestFixtures.unpublished());

        mockMvc.perform(get("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(containsString("UNPUBLISHED")))
                .andExpect(content().string(containsString("/edit")))
                .andExpect(content().string(containsString("/preview")))
                .andExpect(content().string(not(containsString("/articles/admin-draft"))));
    }

    @Test
    void listRequiresAuthenticationAndOwnerApplicationAuthorization() throws Exception {
        mockMvc.perform(get("/admin/content")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/content")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR))))
                .andExpect(status().isForbidden());
    }
}

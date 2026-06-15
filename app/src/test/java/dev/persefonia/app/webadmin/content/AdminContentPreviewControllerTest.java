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
class AdminContentPreviewControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void ownerPreviewRendersSanitizedHtmlWithNoindexAndNoStoreWithoutSaving() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        int savesBefore = items.saveCount();

        mockMvc.perform(get("/admin/content/" + item.id().value() + "/preview")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1 id=\"safe-preview\">Safe preview</h1>")))
                .andExpect(content().string(containsString(
                        "<meta name=\"robots\" content=\"noindex,nofollow,noarchive\">")))
                .andExpect(content().string(not(containsString("<script"))))
                .andExpect(content().string(not(containsString("onerror"))))
                .andExpect(content().string(not(containsString("# Safe preview"))))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
        org.assertj.core.api.Assertions.assertThat(items.saveCount()).isEqualTo(savesBefore);
    }

    @Test
    void previewRequiresAuthenticationAndOwner() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        String path = "/admin/content/" + item.id().value() + "/preview";
        mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
        mockMvc.perform(get(path)
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewWithoutMarkdownShowsClearSafeErrorAndMissingContentIs404() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        item.clearMarkdownSource(AdminContentTestFixtures.CREATED.plusSeconds(10));
        items.add(item);

        mockMvc.perform(get("/admin/content/" + item.id().value() + "/preview")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Preview requires saved Markdown source.")));

        mockMvc.perform(get("/admin/content/" + java.util.UUID.randomUUID() + "/preview")
                        .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                .andExpect(status().isNotFound());
    }
}

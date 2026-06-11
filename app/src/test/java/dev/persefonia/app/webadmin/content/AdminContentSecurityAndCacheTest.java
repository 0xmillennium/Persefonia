package dev.persefonia.app.webadmin.content;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.List;
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
class AdminContentSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void allContentGetRoutesRequireAuthenticationAndAreNoStoreForOwner() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        List<String> paths = List.of(
                "/admin/content",
                "/admin/content/new",
                "/admin/content/" + item.id().value() + "/edit",
                "/admin/content/" + item.id().value() + "/preview");
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
            mockMvc.perform(get(path)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Cache-Control", containsString("private")));
        }
    }

    @Test
    void bothStateChangingRoutesRejectMissingCsrf() throws Exception {
        var item = AdminContentTestFixtures.completeDraft();
        items.add(item);
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
        mockMvc.perform(post("/admin/content").with(owner)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/content/" + item.id().value()).with(owner)).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(items.saveCount()).isZero();
    }
}

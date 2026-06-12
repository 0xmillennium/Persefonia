package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AdminContentLifecycleSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminContentTestRepository items;

    @BeforeEach void reset() { items.reset(); }

    @Test
    void lifecyclePostsRequireAuthenticationAndCsrf() throws Exception {
        var item = AdminContentTestFixtures.published();
        items.add(item);
        for (String action : List.of("publish", "unpublish", "archive")) {
            String path = "/admin/content/" + item.id().value() + "/" + action;
            mockMvc.perform(post(path)).andExpect(status().isForbidden());
            mockMvc.perform(post(path).with(authentication(
                            AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(path)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER)))
                            .with(csrf().useInvalidToken()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER))))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(items.saveCount()).isZero();
    }

    @Test
    void applicationAuthorizationRejectsNonOwnerForEveryLifecycleCommand() throws Exception {
        for (String action : List.of("publish", "unpublish", "archive")) {
            var item = AdminContentTestFixtures.published();
            items.add(item);
            mockMvc.perform(post("/admin/content/" + item.id().value() + "/" + action)
                            .with(authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR)))
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(items.saveCount()).isZero();
    }
}

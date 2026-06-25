package dev.persefonia.app.webadmin.analytics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
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
@Import(AdminAnalyticsTestConfiguration.class)
@ActiveProfiles({"test", "admin-analytics-mvc-test"})
class AdminAnalyticsSecurityAndCacheTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/admin/analytics")).andExpect(status().is4xxClientError());
    }

    @Test
    void nonOwnerAdminIsForbidden() throws Exception {
        var editor = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));

        mockMvc.perform(get("/admin/analytics").with(editor)).andExpect(status().isForbidden());
    }

    @Test
    void ownerSeesReadOnlyAggregateSummaryWithSensitiveCacheHeaders() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(get("/admin/analytics").with(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("PUBLIC_PAGE_VIEW")))
                .andExpect(content().string(containsString("Analytics")));
    }

    @Test
    void analyticsHasNoMutationEndpoint() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/analytics").with(owner).with(csrf()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/admin/analytics").with(owner).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}

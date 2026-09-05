package dev.persefonia.app.webadmin.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.app.webadmin.contact.AdminContactTestConfiguration.ContactMessageStore;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(AdminContactTestConfiguration.class)
@ActiveProfiles({"test", "admin-contact-mvc-test"})
class AdminContactSecurityAndCacheTest {
    @Autowired MockMvc mockMvc;
    @Autowired ContactMessageStore store;

    @BeforeEach
    void reset() {
        store.reset();
        store.save(AdminContactTestConfiguration.message());
    }

    @Test
    void anonymousCannotAccessContactAdminRoutesAndResponsesAreUncached() throws Exception {
        assertProtectedAndUncached(mockMvc.perform(get("/admin/contact")).andReturn());
        assertProtectedAndUncached(mockMvc.perform(get(
                "/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value())).andReturn());
        assertProtectedAndUncached(mockMvc.perform(post(
                "/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/read").with(csrf())).andReturn());
    }

    @Test
    void ownerCanAccessContactAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/contact").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
        mockMvc.perform(get("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void statusPostsRequireCsrfAndRemainUncached() throws Exception {
        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/read")
                        .with(owner()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/read")
                        .with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void nonOwnerStatusPostIsForbiddenAtApplicationLayer() throws Exception {
        mockMvc.perform(post("/admin/contact/" + AdminContactTestConfiguration.MESSAGE_ID.value() + "/spam")
                        .with(editor()).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(store.findById(AdminContactTestConfiguration.MESSAGE_ID).orElseThrow().statusChanges())
                .isEmpty();
    }

    private static void assertProtectedAndUncached(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isBetween(300, 499);
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("no-store").contains("private");
        assertThat(cacheControl).doesNotContain("public");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
    }
}

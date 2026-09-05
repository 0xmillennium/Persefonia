package dev.persefonia.app.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest(
        properties = {"management.server.port=0", "management.health.redis.enabled=false"},
        classes = dev.persefonia.app.PersefoniaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualRedirectPublicRouteIntegrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = postgresContainer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("site.public-base-url", () -> "https://persefonia.test");
    }

    @Test
    void manualRedirectCreatedThroughAdminAffectsPublicGetUntilDeactivated() throws Exception {
        var owner = authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));

        mockMvc.perform(post("/admin/discovery/redirects")
                        .with(owner)
                        .with(csrf())
                        .param("sourceUrl", "/tr/articles/old")
                        .param("targetUrl", "/tr/articles/new")
                        .param("statusCode", "308"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discovery/redirects?created=true"));

        UUID redirectId = jdbc.queryForObject("""
                SELECT id
                FROM discovery.redirect_rules
                WHERE source_url = '/tr/articles/old'
                """, UUID.class);
        assertThat(jdbc.queryForObject("""
                SELECT source_context IS NULL
                    AND source_type IS NULL
                    AND source_entity_id IS NULL
                FROM discovery.redirect_rules
                WHERE id = ?
                """, Boolean.class, redirectId)).isTrue();

        mockMvc.perform(get("/tr/articles/old"))
                .andExpect(status().is(308))
                .andExpect(header().string("Location", "/tr/articles/new"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", "public, no-cache, must-revalidate"))
                .andExpect(content().string(not(containsString("source_entity_id"))));

        mockMvc.perform(post("/admin/discovery/redirects/" + redirectId + "/deactivate")
                        .with(owner)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discovery/redirects?deactivated=true"));
        mockMvc.perform(post("/admin/discovery/redirects/" + redirectId + "/deactivate")
                        .with(owner)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discovery/redirects?alreadyInactive=true"));

        mockMvc.perform(get("/tr/articles/old"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(header().doesNotExist("Location"));
    }

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database postgres = SharedPostgresTestServer.integrationDatabase();
        postgres.withDatabaseName("persefonia_manual_redirect_public_route");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}

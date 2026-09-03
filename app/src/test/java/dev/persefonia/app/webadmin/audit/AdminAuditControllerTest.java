package dev.persefonia.app.webadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import java.time.Instant;
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
@Import(AdminAuditTestConfiguration.class)
@ActiveProfiles({"test", "admin-audit-mvc-test"})
class AdminAuditControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminAuditTestConfiguration.CapturingAuditQueryPort queries;

    @Test
    void ownerListRendersFiltersRowsNavigationEscapingAndSensitiveHeaders() throws Exception {
        mockMvc.perform(get("/admin/audit").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andExpect(content().string(containsString("Audit history")))
                .andExpect(content().string(containsString("aria-current=\"page\">Audit")))
                .andExpect(content().string(containsString("content.published")))
                .andExpect(content().string(containsString("publishing / content_item")))
                .andExpect(content().string(containsString("/admin/audit/" + AdminAuditTestConfiguration.RECORD_ID)))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        assertThat(queries.request).isEqualTo(AuditSearchRequest.firstPage());
    }

    @Test
    void ownerDetailRendersCompleteReadOnlyRecord() throws Exception {
        mockMvc.perform(get("/admin/audit/{id}", AdminAuditTestConfiguration.RECORD_ID).with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andExpect(content().string(containsString("request-123")))
                .andExpect(content().string(containsString("Jane Admin")))
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(containsString("manual review")));
    }

    @Test
    void controllerMapsEveryCanonicalFilterAndNormalizesPagination() throws Exception {
        mockMvc.perform(get("/admin/audit")
                        .param("action", "content.published")
                        .param("actorType", "ADMIN")
                        .param("actorId", AdminAuditTestConfiguration.ACTOR_ID.toString())
                        .param("entityContext", "publishing")
                        .param("entityType", "content_item")
                        .param("entityId", AdminAuditTestConfiguration.ENTITY_ID.toString())
                        .param("from", "2026-09-03T17:00:00Z")
                        .param("to", "2026-09-04T17:00:00Z")
                        .param("page", "0")
                        .param("pageSize", "500")
                        .with(owner()))
                .andExpect(status().isOk());

        assertThat(queries.request.action().value()).isEqualTo("content.published");
        assertThat(queries.request.actorType().name()).isEqualTo("ADMIN");
        assertThat(queries.request.actorId().value()).isEqualTo(AdminAuditTestConfiguration.ACTOR_ID);
        assertThat(queries.request.entityContext().value()).isEqualTo("publishing");
        assertThat(queries.request.entityType().value()).isEqualTo("content_item");
        assertThat(queries.request.entityId().value()).isEqualTo(AdminAuditTestConfiguration.ENTITY_ID);
        assertThat(queries.request.occurredFromInclusive()).isEqualTo(Instant.parse("2026-09-03T17:00:00Z"));
        assertThat(queries.request.occurredToExclusive()).isEqualTo(Instant.parse("2026-09-04T17:00:00Z"));
        assertThat(queries.request.page()).isEqualTo(1);
        assertThat(queries.request.pageSize()).isEqualTo(100);
    }

    @Test
    void malformedAndInvalidFilterCombinationsReturnBadRequestWithoutEcho() throws Exception {
        String[][] invalid = {
                {"action", "Bad Action"},
                {"actorType", "ROOT"},
                {"actorId", "not-a-uuid"},
                {"actorType", "SYSTEM", "actorId", AdminAuditTestConfiguration.ACTOR_ID.toString()},
                {"entityType", "content_item"},
                {"entityContext", "publishing", "entityId", AdminAuditTestConfiguration.ENTITY_ID.toString()},
                {"entityContext", "publishing", "entityType", "content_item", "entityId", "bad-uuid"},
                {"from", "not-an-instant"},
                {"from", "2026-09-03T17:00:00Z", "to", "2026-09-03T17:00:00Z"},
                {"from", "2026-09-04T17:00:00Z", "to", "2026-09-03T17:00:00Z"}
        };
        for (String[] parameters : invalid) {
            var request = get("/admin/audit").with(owner());
            for (int index = 0; index < parameters.length; index += 2) {
                request.param(parameters[index], parameters[index + 1]);
            }
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString(parameters[1]))));
        }
    }

    @Test
    void malformedAndMissingDetailIdsBothReturnNotFound() throws Exception {
        mockMvc.perform(get("/admin/audit/not-a-uuid").with(owner())).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/audit/{id}", java.util.UUID.randomUUID()).with(owner()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAndDetailAreOwnerOnly() throws Exception {
        String detail = "/admin/audit/" + AdminAuditTestConfiguration.RECORD_ID;
        mockMvc.perform(get("/admin/audit")).andExpect(status().is4xxClientError());
        mockMvc.perform(get(detail)).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/audit").with(editor())).andExpect(status().isForbidden());
        mockMvc.perform(get(detail).with(editor())).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit").with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(detail).with(owner())).andExpect(status().isOk());
    }

    @Test
    void auditHasNoMutationEndpoint() throws Exception {
        mockMvc.perform(post("/admin/audit").with(owner()).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
    }
}

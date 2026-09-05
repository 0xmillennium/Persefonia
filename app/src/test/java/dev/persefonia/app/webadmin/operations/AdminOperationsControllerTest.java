package dev.persefonia.app.webadmin.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.persefonia.app.security.admin.AdminAuthenticationTestSupport;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.platformoperations.application.operations.CacheRecoveryAction;
import dev.persefonia.platformoperations.application.operations.CacheRecoveryCommandResult;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import org.junit.jupiter.api.*;
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
@Import(AdminOperationsTestConfiguration.class)
@ActiveProfiles({"test", "admin-operations-mvc-test"})
class AdminOperationsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AdminOperationsTestConfiguration.TestQueries queries;
    @Autowired AdminOperationsTestConfiguration.TestRecovery recovery;
    @Autowired AdminOperationsTestConfiguration.TestRecoveryVerification verification;

    @BeforeEach void reset() {
        recovery.result = CacheRecoveryCommandResult.ACCEPTED;
        recovery.initial = recovery.retry = recovery.resume = 0;
        queries.detailAction = CacheRecoveryAction.RESUME_STRANDED;
        verification.contextCalls = verification.verifyCalls = 0;
    }

    @Test
    void ownerRecoveryGetIsCheapPrivateAndShowsSafeCompatibilityContext() throws Exception {
        mvc.perform(get("/admin/operations/recovery").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(containsString("no-store"), containsString("private"))))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andExpect(content().string(containsString("0.1.0")))
                .andExpect(content().string(containsString("PostgreSQL business metadata together with durable Media storage")))
                .andExpect(content().string(containsString("does not prove that external backups were captured from the same recovery point")))
                .andExpect(content().string(containsString("Deep verification has not been run")));
        assertThat(verification.contextCalls).isEqualTo(1);
        assertThat(verification.verifyCalls).isZero();
    }

    @Test
    void ownerRecoveryPostRequiresCsrfAndRendersEphemeralEscapedReportExactlyOnce() throws Exception {
        mvc.perform(post("/admin/operations/recovery/verify").with(owner()))
                .andExpect(status().isForbidden());
        assertThat(verification.verifyCalls).isZero();

        mvc.perform(post("/admin/operations/recovery/verify").with(owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INCONSISTENT")))
                .andExpect(content().string(containsString(AdminOperationsTestConfiguration.NOW.toString())))
                .andExpect(content().string(containsString("CHECKSUM_MISMATCH")))
                .andExpect(content().string(containsString("DISCOVERY_OG_IMAGE")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
        assertThat(verification.verifyCalls).isEqualTo(1);
    }

    @Test
    void recoveryRoutesAreOwnerOnly() throws Exception {
        mvc.perform(get("/admin/operations/recovery")).andExpect(status().is4xxClientError());
        mvc.perform(post("/admin/operations/recovery/verify").with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(get("/admin/operations/recovery").with(editor())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/operations/recovery/verify").with(editor()).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(verification.contextCalls + verification.verifyCalls).isZero();
    }

    @Test
    void ownerListShowsStatusSummaryRowsNavigationPaginationAndPrivateHeaders() throws Exception {
        mvc.perform(get("/admin/operations").param("status", "RUNNING").param("page", "2")
                        .param("pageSize", "25").with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(containsString("no-store"), containsString("private"))))
                .andExpect(content().string(containsString("noindex,nofollow,noarchive")))
                .andExpect(content().string(containsString("aria-current=\"page\">Operations")))
                .andExpect(content().string(containsString("System status")))
                .andExpect(content().string(containsString("Cache invalidation summary")))
                .andExpect(content().string(containsString("STRANDED")))
                .andExpect(content().string(containsString(AdminOperationsTestConfiguration.BATCH_ID.toString())));
        assertThat(queries.request.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
        assertThat(queries.request.page()).isEqualTo(2);
    }

    @Test
    void ownerDetailShowsOnlyStrandedResumeWarningValidatedTargetAndEscapesHtml() throws Exception {
        mvc.perform(get("/admin/operations/cache/{id}", AdminOperationsTestConfiguration.BATCH_ID).with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Resume stranded purge")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("may repeat an external operation")))
                .andExpect(content().string(containsString("/safe/&lt;script&gt;")))
                .andExpect(content().string(not(containsString("/safe/<script>"))))
                .andExpect(content().string(not(containsString("Retry failed targets"))))
                .andExpect(content().string(not(containsString("Execute initial purge"))));
    }

    @Test
    void detailShowsOnlyTheServerDerivedEligibleRecoveryAction() throws Exception {
        String detail = "/admin/operations/cache/" + AdminOperationsTestConfiguration.BATCH_ID;

        queries.detailAction = CacheRecoveryAction.EXECUTE_INITIAL;
        mvc.perform(get(detail).with(owner()))
                .andExpect(content().string(containsString("Execute initial purge")))
                .andExpect(content().string(not(containsString("Retry failed targets"))))
                .andExpect(content().string(not(containsString("Resume stranded purge"))));

        queries.detailAction = CacheRecoveryAction.RETRY_FAILED;
        mvc.perform(get(detail).with(owner()))
                .andExpect(content().string(containsString("Retry failed targets")))
                .andExpect(content().string(not(containsString("Execute initial purge"))))
                .andExpect(content().string(not(containsString("Resume stranded purge"))));

        queries.detailAction = CacheRecoveryAction.NONE;
        mvc.perform(get(detail).with(owner()))
                .andExpect(content().string(containsString("No recovery action is currently available.")))
                .andExpect(content().string(not(containsString("Execute initial purge"))))
                .andExpect(content().string(not(containsString("Retry failed targets"))))
                .andExpect(content().string(not(containsString("Resume stranded purge"))));
    }

    @Test
    void routesAreOwnerOnlyForGetAndPost() throws Exception {
        String detail = "/admin/operations/cache/" + AdminOperationsTestConfiguration.BATCH_ID;
        mvc.perform(get("/admin/operations")).andExpect(status().is4xxClientError());
        mvc.perform(get(detail)).andExpect(status().is4xxClientError());
        for (String action : java.util.List.of("execute", "retry", "resume")) {
            mvc.perform(post(detail + "/" + action).with(csrf())).andExpect(status().is4xxClientError());
        }
        mvc.perform(get("/admin/operations").with(editor())).andExpect(status().isForbidden());
        mvc.perform(get(detail).with(editor())).andExpect(status().isForbidden());
        for (String action : java.util.List.of("execute", "retry", "resume")) {
            mvc.perform(post(detail + "/" + action).with(editor()).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/operations").with(owner())).andExpect(status().isOk());
        assertThat(recovery.initial + recovery.retry + recovery.resume).isZero();
    }

    @Test
    void recoveryPostsRequireCsrfUsePrgAndMapTypedResults() throws Exception {
        String root = "/admin/operations/cache/" + AdminOperationsTestConfiguration.BATCH_ID;
        mvc.perform(post(root + "/resume").with(owner())).andExpect(status().isForbidden());
        assertThat(recovery.resume).isZero();
        mvc.perform(post(root + "/resume").with(owner()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(root));
        assertThat(recovery.resume).isEqualTo(1);

        recovery.result = CacheRecoveryCommandResult.NOT_ELIGIBLE;
        mvc.perform(post(root + "/retry").with(owner()).with(csrf())).andExpect(status().isConflict());
        recovery.result = CacheRecoveryCommandResult.NOT_FOUND;
        mvc.perform(post(root + "/execute").with(owner()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void malformedAndMissingIdsAreNotFoundAndGetCannotMutate() throws Exception {
        mvc.perform(get("/admin/operations/cache/not-a-uuid").with(owner())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/operations/cache/{id}", java.util.UUID.randomUUID()).with(owner()))
                .andExpect(status().isNotFound());
        String root = "/admin/operations/cache/" + AdminOperationsTestConfiguration.BATCH_ID;
        mvc.perform(get(root + "/execute").with(owner())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(root + "/retry").with(owner())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(root + "/resume").with(owner())).andExpect(status().isMethodNotAllowed());
        assertThat(recovery.initial + recovery.retry + recovery.resume).isZero();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.OWNER));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return authentication(AdminAuthenticationTestSupport.authentication(AdminRole.EDITOR));
    }
}

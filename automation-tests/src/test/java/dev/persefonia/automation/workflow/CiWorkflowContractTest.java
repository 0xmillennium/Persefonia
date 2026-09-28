package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CiWorkflowContractTest {
    private final WorkflowDocument workflow = WorkflowDocument.open("ci.yml");

    CiWorkflowContractTest() throws IOException {}

    @Test
    void acceptsOnlyMasterPullRequestsPushesAndManualDispatch() {
        assertCiTriggers(workflow);
        for (String unexpected : List.of("schedule", "pull_request_target", "repository_dispatch", "workflow_call")) {
            WorkflowDocument changed = openCi();
            changed.triggers().put(unexpected, Map.of());
            assertThatThrownBy(() -> assertCiTriggers(changed)).as(unexpected).isInstanceOf(AssertionError.class);
        }
        WorkflowDocument broadened = openCi();
        WorkflowDocument.map(broadened.triggers().get("push")).put("branches", List.of("master", "feature"));
        assertThatThrownBy(() -> assertCiTriggers(broadened)).isInstanceOf(AssertionError.class);
        WorkflowDocument tags = openCi();
        WorkflowDocument.map(tags.triggers().get("push")).put("tags", List.of("*"));
        assertThatThrownBy(() -> assertCiTriggers(tags)).isInstanceOf(AssertionError.class);
    }

    @Test
    void ciServicesRemainDigestPinned() {
        assertDigestPinnedServices(workflow);
        for (String mutable : List.of("postgres:17", "redis:latest", "redis@sha256:short")) {
            WorkflowDocument changed = openCi();
            WorkflowDocument.map(WorkflowDocument.map(changed.job("verify").get("services")).get("redis"))
                    .put("image", mutable);
            assertThatThrownBy(() -> assertDigestPinnedServices(changed)).as(mutable)
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void ciConcurrencyAndRootPermissionsMatchAcceptedScope() {
        assertThat(WorkflowDocument.map(workflow.root().get("concurrency")))
                .containsEntry("group", "ci-${{ github.event.pull_request.number || github.sha }}")
                .containsEntry("cancel-in-progress", "${{ github.event_name == 'pull_request' }}");
        assertThat(WorkflowDocument.map(workflow.root().get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
    }

    private static WorkflowDocument openCi() {
        try {
            return WorkflowDocument.open("ci.yml");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertCiTriggers(WorkflowDocument ci) {
        assertThat(ci.triggers()).containsOnlyKeys("pull_request", "push", "workflow_dispatch");
        for (String event : List.of("pull_request", "push")) {
            assertThat(WorkflowDocument.map(ci.triggers().get(event)))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("branches", List.of("master")));
        }
        assertThat(ci.triggers().get("workflow_dispatch")).isNull();
    }

    private static void assertDigestPinnedServices(WorkflowDocument ci) {
        Map<String, Object> services = WorkflowDocument.map(ci.job("verify").get("services"));
        assertThat(services).containsOnlyKeys("postgres", "redis");
        for (var service : services.entrySet()) {
            assertThat((String) WorkflowDocument.map(service.getValue()).get("image"))
                    .as("verify/services/" + service.getKey() + "/image")
                    .matches("[^\\s@]+@sha256:[0-9a-f]{64}");
        }
    }

    @Test
    void automationFailsBeforeApplicationInfrastructureStarts() {
        assertThat(workflow.jobs()).containsOnlyKeys("automation", "verify", "gate");
        Map<String, Object> automation = workflow.job("automation");
        Map<String, Object> verify = workflow.job("verify");
        assertThat(WorkflowDocument.map(automation.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(WorkflowDocument.map(verify.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(automation).doesNotContainKeys("services");
        assertThat(automation.get("needs")).isNull();
        assertThat(verify.get("needs")).isEqualTo("automation");
        assertThat(WorkflowDocument.map(verify.get("services"))).containsKeys("postgres", "redis");
        List<String> names = workflow.steps("automation").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")).toList();
        assertThat(names).containsSubsequence("Install verified automation analysis tools", "Verify automation sources",
                "Set up Temurin Java 25", "Verify automation contracts");
        assertThat(names).doesNotContain("Set up Node 22", "Build and test");
        assertThat(workflow.step("automation", "Verify automation sources").get("run"))
                .asString().contains("./scripts/ci/verify-automation-source.sh");
        assertThat(workflow.step("automation", "Verify automation contracts").get("run"))
                .asString().contains("automationCheck");
        assertThat(workflow.step("verify", "Build and test").get("run"))
                .asString().contains("applicationCheck", ":app:bootJar");
    }

    @Test
    void stableGateRequiresBothResultsAndHasNoRepositoryOrSecretAccess() {
        Map<String, Object> gate = workflow.job("gate");
        assertThat(WorkflowDocument.list(gate.get("needs"))).containsExactlyInAnyOrder("automation", "verify");
        assertThat(gate.get("if")).isEqualTo("always()");
        assertThat(WorkflowDocument.map(gate.get("permissions"))).isEmpty();
        assertThat(gate).doesNotContainKeys("services", "environment");
        assertThat(workflow.steps("gate")).hasSize(1);
        Map<String, Object> step = WorkflowDocument.map(workflow.steps("gate").getFirst());
        assertThat(step).doesNotContainKeys("uses", "secrets");
        assertThat(WorkflowDocument.map(step.get("env"))).containsKeys("AUTOMATION_RESULT", "VERIFY_RESULT");
        assertThat((String) step.get("run")).contains("$AUTOMATION_RESULT", "$VERIFY_RESULT", "success");
    }
}

package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CiWorkflowContractTest {
    private final WorkflowDocument workflow = WorkflowDocument.open("ci.yml");

    CiWorkflowContractTest() throws IOException {}

    @Test
    void automationFailsBeforeApplicationInfrastructureStarts() {
        Map<String, Object> automation = workflow.job("automation");
        Map<String, Object> verify = workflow.job("verify");
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

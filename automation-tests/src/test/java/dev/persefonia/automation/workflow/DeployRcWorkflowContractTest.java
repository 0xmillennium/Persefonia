package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeployRcWorkflowContractTest {
    private final WorkflowDocument workflow = WorkflowDocument.open("deploy-rc.yml");

    DeployRcWorkflowContractTest() throws IOException {}

    @Test
    void qualificationPrecedesEnvironmentDeployment() {
        Map<String, Object> trigger = WorkflowDocument.map(workflow.triggers().get("workflow_run"));
        assertThat(workflow.triggers()).containsOnlyKeys("workflow_run");
        assertThat(WorkflowDocument.list(trigger.get("workflows"))).containsExactly("Delivery");
        assertThat(WorkflowDocument.list(trigger.get("branches"))).containsExactly("master");
        String guard = (String) workflow.job("resolve-qualified-artifact").get("if");
        assertThat(guard).contains("conclusion == 'success'", "event == 'workflow_run'", "head_branch == 'master'",
                "head_repository.full_name == github.repository", "head_repository.fork == false",
                "github.sha == github.event.workflow_run.head_sha");
        assertThat(workflow.job("resolve-qualified-artifact")).doesNotContainKey("environment");
        assertThat(workflow.job("deploy-rc").get("needs")).isEqualTo("resolve-qualified-artifact");
        assertThat(WorkflowDocument.map(workflow.job("deploy-rc").get("environment"))).containsEntry("name", "rc");
        assertThat(WorkflowDocument.map(workflow.step("deploy-rc", "Check out qualified source").get("with")))
                .containsEntry("ref", "${{ needs.resolve-qualified-artifact.outputs.source_sha }}");
        assertThat((String) workflow.step("resolve-qualified-artifact", "Resolve and verify qualified artifact").get("run"))
                .contains("resolve-qualified-artifact.sh");
    }

    @Test
    void sshTrustAndDeploymentRemainNarrowlyScoped() {
        List<String> names = workflow.steps("deploy-rc").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")).toList();
        assertThat(names).containsSubsequence("Materialize RC SSH private key", "Verify RC SSH target",
                "Deploy qualified artifact", "Verify RC public ingress");
        assertThat(workflow.step("deploy-rc", "Remove RC SSH material").get("if")).isEqualTo("always()");
        assertThat((String) workflow.step("deploy-rc", "Verify RC SSH target").get("run"))
                .contains("verify-ssh-target.sh");
        assertThat((String) workflow.step("deploy-rc", "Deploy qualified artifact").get("run"))
                .contains("run-rc-deployment.sh");
        assertThat((String) workflow.step("deploy-rc", "Verify RC public ingress").get("run"))
                .contains("verify-rc-ingress.sh");
        for (Object value : workflow.steps("resolve-qualified-artifact")) {
            Map<String, Object> step = WorkflowDocument.map(value);
            assertThat(step.toString()).doesNotContain("secrets.", "RC_SSH_PRIVATE_KEY");
        }
        for (Object value : workflow.steps("deploy-rc")) {
            Map<String, Object> step = WorkflowDocument.map(value);
            if (step.get("run") instanceof String run) {
                assertThat(run).doesNotContain("docker ", "docker compose", "sudo ");
            }
        }
    }
}

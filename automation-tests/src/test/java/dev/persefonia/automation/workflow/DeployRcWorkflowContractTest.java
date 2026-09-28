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
        assertThat(WorkflowDocument.list(trigger.get("types"))).containsExactly("completed");
        assertThat(workflow.jobs()).containsOnlyKeys("resolve-qualified-artifact", "deploy-rc");
        assertThat(WorkflowDocument.map(workflow.root().get("permissions"))).isEmpty();
        assertThat(WorkflowDocument.map(workflow.root().get("concurrency")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("group", "rc-deployment", "cancel-in-progress", false));
        String guard = (String) workflow.job("resolve-qualified-artifact").get("if");
        assertThat(guard).contains("conclusion == 'success'", "event == 'workflow_run'", "head_branch == 'master'",
                "head_repository.full_name == github.repository", "head_repository.fork == false",
                "github.sha == github.event.workflow_run.head_sha");
        assertThat(workflow.job("resolve-qualified-artifact")).doesNotContainKey("environment");
        assertThat(WorkflowDocument.map(workflow.job("resolve-qualified-artifact").get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read", "packages", "read", "actions", "read"));
        assertThat(workflow.job("deploy-rc").get("needs")).isEqualTo("resolve-qualified-artifact");
        assertThat(WorkflowDocument.map(workflow.job("deploy-rc").get("environment")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("name", "rc"));
        assertThat(WorkflowDocument.map(workflow.job("deploy-rc").get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(WorkflowDocument.map(workflow.step("deploy-rc", "Check out qualified source").get("with")))
                .containsEntry("ref", "${{ needs.resolve-qualified-artifact.outputs.source_sha }}");
        assertThat((String) workflow.step("resolve-qualified-artifact", "Resolve and verify qualified artifact").get("run"))
                .contains("resolve-qualified-artifact.sh");
    }

    @Test
    void sshTrustAndDeploymentRemainNarrowlyScoped() {
        assertThat(workflow.job("deploy-rc")).doesNotContainKey("env");
        List<String> names = workflow.steps("deploy-rc").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")).toList();
        assertThat(names).containsExactly("Check out qualified source", "Assert checked out qualified source",
                "Materialize RC SSH private key", "Verify RC SSH target", "Deploy qualified artifact",
                "Verify RC public ingress", "Write RC deployment summary", "Remove RC SSH material");
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
            if (!"Materialize RC SSH private key".equals(step.get("name"))) {
                assertThat(step.toString()).doesNotContain("secrets.");
            }
            if (step.get("run") instanceof String run) {
                assertThat(run).doesNotContain("docker ", "docker compose", "sudo ");
            }
        }
    }

    @Test
    void triggeringHandoffAndResolverStayBoundToTheSameDeliveryRun() {
        assertThat(workflow.steps("resolve-qualified-artifact").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")).toList())
                .containsExactly("Check out Delivery source", "Assert checked out source",
                        "Log in to GitHub Container Registry", "Download triggering Delivery handoff",
                        "Verify triggering Delivery handoff", "Resolve and verify qualified artifact");
        Map<String, Object> download = workflow.step("resolve-qualified-artifact", "Download triggering Delivery handoff");
        assertThat((String) download.get("uses")).startsWith("actions/download-artifact@");
        assertThat(WorkflowDocument.map(download.get("with")))
                .containsEntry("name", "persefonia-delivery-handoff-${{ github.event.workflow_run.id }}-${{ github.event.workflow_run.run_attempt }}")
                .containsEntry("run-id", "${{ github.event.workflow_run.id }}")
                .containsEntry("repository", "${{ github.repository }}")
                .containsEntry("github-token", "${{ github.token }}")
                .containsEntry("path", "delivery-handoff");
        Map<String, Object> handoff = workflow.step("resolve-qualified-artifact", "Verify triggering Delivery handoff");
        assertThat(handoff).containsEntry("id", "handoff");
        assertThat(WorkflowDocument.map(handoff.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "EXPECTED_SOURCE_SHA", "${{ github.event.workflow_run.head_sha }}",
                        "DELIVERY_RUN_ID", "${{ github.event.workflow_run.id }}",
                        "DELIVERY_RUN_ATTEMPT", "${{ github.event.workflow_run.run_attempt }}"));
        assertThat((String) handoff.get("run")).contains(
                "./scripts/deploy/verify-delivery-handoff.sh",
                "delivery-handoff/delivery-handoff.txt",
                "\"$EXPECTED_SOURCE_SHA\" \"$DELIVERY_RUN_ID\" \"$DELIVERY_RUN_ATTEMPT\"",
                "\"$GITHUB_REPOSITORY\" >> \"$GITHUB_OUTPUT\"");

        Map<String, Object> login = workflow.step("resolve-qualified-artifact", "Log in to GitHub Container Registry");
        assertThat((String) login.get("uses")).startsWith("docker/login-action@");
        assertThat(WorkflowDocument.map(login.get("with")))
                .containsEntry("registry", "ghcr.io")
                .containsEntry("username", "${{ github.actor }}")
                .containsEntry("password", "${{ github.token }}");
        Map<String, Object> resolver = workflow.step("resolve-qualified-artifact", "Resolve and verify qualified artifact");
        assertThat(resolver).containsEntry("id", "artifact");
        assertThat(WorkflowDocument.map(resolver.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "EXPECTED_SOURCE_SHA", "${{ github.event.workflow_run.head_sha }}",
                        "EXPECTED_IMAGE_DIGEST", "${{ steps.handoff.outputs.expected_image_digest }}",
                        "GH_TOKEN", "${{ github.token }}"));
        assertThat(workflow.steps("resolve-qualified-artifact").stream().map(WorkflowDocument::map)
                .filter(step -> step.containsKey("env")
                        && WorkflowDocument.map(step.get("env")).containsKey("GH_TOKEN"))
                .map(step -> step.get("name")).toList())
                .containsExactly("Resolve and verify qualified artifact");
        assertThat(resolver.get("run")).isEqualTo("./scripts/deploy/resolve-qualified-artifact.sh "
                + "\"$EXPECTED_SOURCE_SHA\" \"$EXPECTED_IMAGE_DIGEST\" \"$GITHUB_REPOSITORY\" "
                + "docker/supported-platforms.txt >> \"$GITHUB_OUTPUT\"");
        Map<String, Object> outputs = WorkflowDocument.map(workflow.job("resolve-qualified-artifact").get("outputs"));
        assertThat(outputs).containsOnlyKeys("source_sha", "image_name", "image_digest", "image_reference", "source_alias");
        for (String output : outputs.keySet()) {
            assertThat(outputs).containsEntry(output, "${{ steps.artifact.outputs." + output + " }}");
        }
    }

    @Test
    void sshMaterialDeploymentSummaryAndCleanupUseTheQualifiedOutputs() {
        Map<String, Object> materialize = workflow.step("deploy-rc", "Materialize RC SSH private key");
        assertThat(WorkflowDocument.map(materialize.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("RC_SSH_PRIVATE_KEY", "${{ secrets.RC_SSH_PRIVATE_KEY }}"));
        assertThat((String) materialize.get("run")).contains("test -n \"$RC_SSH_PRIVATE_KEY\"", "umask 077",
                "printf '%s\\n' \"$RC_SSH_PRIVATE_KEY\" > \"$RUNNER_TEMP/persefonia-rc-ssh-key\"",
                "chmod 600 \"$RUNNER_TEMP/persefonia-rc-ssh-key\"");

        Map<String, Object> target = workflow.step("deploy-rc", "Verify RC SSH target");
        assertThat(WorkflowDocument.map(target.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "RC_SSH_HOST", "${{ vars.RC_SSH_HOST }}",
                        "RC_SSH_PORT", "${{ vars.RC_SSH_PORT }}",
                        "RC_SSH_USER", "${{ vars.RC_SSH_USER }}",
                        "RC_SSH_HOST_KEY_SHA256", "${{ vars.RC_SSH_HOST_KEY_SHA256 }}"));
        assertThat((String) target.get("run")).contains("./scripts/deploy/verify-ssh-target.sh",
                "\"$RC_SSH_HOST\" \"$RC_SSH_PORT\" \"$RC_SSH_USER\"",
                "\"$RC_SSH_HOST_KEY_SHA256\" \"$RUNNER_TEMP/persefonia-rc-ssh-key\"",
                "\"$RUNNER_TEMP/persefonia-rc-known-hosts\"");

        Map<String, Object> deploy = workflow.step("deploy-rc", "Deploy qualified artifact");
        assertThat(deploy).containsEntry("id", "deployment");
        assertThat(WorkflowDocument.map(deploy.get("env")))
                .containsEntry("QUALIFIED_SOURCE_SHA", "${{ needs.resolve-qualified-artifact.outputs.source_sha }}")
                .containsEntry("QUALIFIED_IMAGE_REFERENCE", "${{ needs.resolve-qualified-artifact.outputs.image_reference }}");
        assertThat((String) deploy.get("run")).contains("./scripts/deploy/run-rc-deployment.sh",
                "\"$RUNNER_TEMP/persefonia-rc-ssh-key\"",
                "\"$RUNNER_TEMP/persefonia-rc-known-hosts\"",
                "\"$QUALIFIED_SOURCE_SHA\" \"$QUALIFIED_IMAGE_REFERENCE\" >> \"$GITHUB_OUTPUT\"");
        assertThat(workflow.step("deploy-rc", "Verify RC public ingress").get("run"))
                .isEqualTo("./scripts/deploy/verify-rc-ingress.sh");

        Map<String, Object> summary = workflow.step("deploy-rc", "Write RC deployment summary");
        Map<String, Object> summaryEnv = WorkflowDocument.map(summary.get("env"));
        assertThat(summaryEnv).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DEPLOYED_SOURCE_SHA", "${{ steps.deployment.outputs.source_sha }}",
                "DEPLOYED_IMAGE_REFERENCE", "${{ steps.deployment.outputs.image_reference }}",
                "POSTGRES_CONTAINER_ID", "${{ steps.deployment.outputs.postgres_container_id }}",
                "REDIS_CONTAINER_ID", "${{ steps.deployment.outputs.redis_container_id }}",
                "APP_CONTAINER_ID", "${{ steps.deployment.outputs.app_container_id }}",
                "APP_IMAGE_ID", "${{ steps.deployment.outputs.app_image_id }}",
                "APP_HEALTH", "${{ steps.deployment.outputs.app_health }}"));
        assertThat((String) summary.get("run")).contains("$GITHUB_STEP_SUMMARY");
        Map<String, Object> cleanup = workflow.step("deploy-rc", "Remove RC SSH material");
        assertThat(cleanup.get("if")).isEqualTo("always()");
        assertThat(cleanup.get("run")).isEqualTo("rm -f -- \"$RUNNER_TEMP/persefonia-rc-ssh-key\" "
                + "\"$RUNNER_TEMP/persefonia-rc-known-hosts\"");
    }
}

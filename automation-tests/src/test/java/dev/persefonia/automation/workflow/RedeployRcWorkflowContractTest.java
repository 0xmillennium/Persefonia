package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedeployRcWorkflowContractTest {
    private final WorkflowDocument redeploy = WorkflowDocument.open("redeploy-rc.yml");
    private final WorkflowDocument automatic = WorkflowDocument.open("deploy-rc.yml");

    RedeployRcWorkflowContractTest() throws IOException {}

    @Test
    void dispatchTakesOnlyStrictArtifactAuthorityAndSerializesWithAutomaticDeploy() {
        assertThat(redeploy.triggers()).containsOnlyKeys("workflow_dispatch");
        Map<String, Object> inputs = WorkflowDocument.map(
                WorkflowDocument.map(redeploy.triggers().get("workflow_dispatch")).get("inputs"));
        assertThat(inputs).containsOnlyKeys("source_sha", "image_digest");
        for (String input : inputs.keySet()) {
            assertThat(WorkflowDocument.map(inputs.get(input)))
                    .containsEntry("required", true).containsEntry("type", "string");
        }
        assertThat(redeploy.jobs()).containsOnlyKeys("resolve-qualified-artifact", "deploy-rc");
        assertThat(WorkflowDocument.map(redeploy.root().get("permissions"))).isEmpty();
        assertThat(WorkflowDocument.map(redeploy.root().get("concurrency")))
                .containsExactlyInAnyOrderEntriesOf(WorkflowDocument.map(automatic.root().get("concurrency")))
                .containsEntry("group", "rc-deployment").containsEntry("cancel-in-progress", false);

        Map<String, Object> validation = redeploy.step("resolve-qualified-artifact", "Validate redeployment inputs");
        assertThat(redeploy.steps("resolve-qualified-artifact").getFirst()).isSameAs(validation);
        assertThat(WorkflowDocument.map(validation.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "REQUESTED_SOURCE_SHA", "${{ inputs.source_sha }}",
                        "REQUESTED_IMAGE_DIGEST", "${{ inputs.image_digest }}"));
        assertThat((String) validation.get("run"))
                .contains("$GITHUB_REF\" == refs/heads/master", "^[0-9a-f]{40}$",
                        "^sha256:[0-9a-f]{64}$")
                .doesNotContain("tr '", "tolower", "${GITHUB_REPOSITORY,,}");
        assertThat(WorkflowDocument.map(redeploy.step("resolve-qualified-artifact",
                "Check out trusted deployment source").get("with")))
                .containsEntry("ref", "${{ github.sha }}").containsEntry("persist-credentials", false);
        assertThat((String) redeploy.step("resolve-qualified-artifact", "Assert checked out deployment source").get("run"))
                .contains("git rev-parse HEAD", "$EXPECTED_WORKFLOW_SHA");
    }

    @Test
    void existingResolverRequalifiesSuppliedDigestBeforeAnyRcCredentialOrMutation() {
        assertThat(redeploy.job("resolve-qualified-artifact")).doesNotContainKey("environment");
        assertThat(redeploy.job("deploy-rc").get("needs")).isEqualTo("resolve-qualified-artifact");
        assertThat(WorkflowDocument.map(redeploy.job("deploy-rc").get("environment")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("name", "rc"));
        assertThat(WorkflowDocument.map(redeploy.job("resolve-qualified-artifact").get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read", "packages", "read"));
        assertThat(WorkflowDocument.map(redeploy.job("deploy-rc").get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));

        Map<String, Object> resolver = redeploy.step("resolve-qualified-artifact", "Resolve and verify qualified artifact");
        assertThat(resolver).containsEntry("id", "artifact");
        assertThat(WorkflowDocument.map(resolver.get("env")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "EXPECTED_SOURCE_SHA", "${{ inputs.source_sha }}",
                        "EXPECTED_IMAGE_DIGEST", "${{ inputs.image_digest }}",
                        "GH_TOKEN", "${{ github.token }}"));
        assertThat(resolver.get("run")).isEqualTo(
                automatic.step("resolve-qualified-artifact", "Resolve and verify qualified artifact").get("run"));
        assertThat((String) resolver.get("run"))
                .contains("\"$EXPECTED_SOURCE_SHA\" \"$EXPECTED_IMAGE_DIGEST\" \"$GITHUB_REPOSITORY\"")
                .contains("docker/supported-platforms.txt >> \"$GITHUB_OUTPUT\"");
        assertThat(WorkflowDocument.map(redeploy.job("resolve-qualified-artifact").get("outputs")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "source_sha", "${{ steps.artifact.outputs.source_sha }}",
                        "image_reference", "${{ steps.artifact.outputs.image_reference }}"));
        List<String> names = redeploy.steps("resolve-qualified-artifact").stream()
                .map(WorkflowDocument::map).map(step -> (String) step.get("name")).toList();
        assertThat(names).containsExactly("Validate redeployment inputs", "Check out trusted deployment source",
                "Assert checked out deployment source", "Log in to GitHub Container Registry",
                "Resolve and verify qualified artifact");
        assertThat(redeploy.step("resolve-qualified-artifact", "Log in to GitHub Container Registry").get("uses"))
                .isEqualTo(automatic.step("resolve-qualified-artifact", "Log in to GitHub Container Registry").get("uses"));
        for (Object value : redeploy.steps("resolve-qualified-artifact")) {
            assertThat(WorkflowDocument.map(value).toString()).doesNotContain("secrets.", "RC_SSH_PRIVATE_KEY");
        }
    }

    @Test
    void deploymentReusesAcceptedSshRuntimeHealthAndIngressContract() {
        List<String> names = redeploy.steps("deploy-rc").stream()
                .map(WorkflowDocument::map).map(step -> (String) step.get("name")).toList();
        assertThat(names).containsExactly("Check out trusted deployment source", "Assert checked out deployment source",
                "Materialize RC SSH private key", "Verify RC SSH target", "Deploy qualified artifact",
                "Verify RC public ingress", "Write RC deployment summary", "Remove RC SSH material");
        assertThat(WorkflowDocument.map(redeploy.step("deploy-rc", "Check out trusted deployment source").get("with")))
                .containsEntry("ref", "${{ github.sha }}").containsEntry("persist-credentials", false);
        for (String step : List.of("Materialize RC SSH private key", "Verify RC SSH target",
                "Deploy qualified artifact", "Verify RC public ingress", "Remove RC SSH material")) {
            assertThat(redeploy.step("deploy-rc", step))
                    .containsAllEntriesOf(automatic.step("deploy-rc", step));
        }
        Map<String, Object> deploy = redeploy.step("deploy-rc", "Deploy qualified artifact");
        assertThat(WorkflowDocument.map(deploy.get("env")))
                .containsEntry("QUALIFIED_SOURCE_SHA", "${{ needs.resolve-qualified-artifact.outputs.source_sha }}")
                .containsEntry("QUALIFIED_IMAGE_REFERENCE", "${{ needs.resolve-qualified-artifact.outputs.image_reference }}");
        assertThat((String) deploy.get("run")).contains("run-rc-deployment.sh",
                "\"$QUALIFIED_SOURCE_SHA\" \"$QUALIFIED_IMAGE_REFERENCE\" >> \"$GITHUB_OUTPUT\"");
        assertThat((String) redeploy.step("deploy-rc", "Verify RC SSH target").get("run"))
                .contains("verify-ssh-target.sh");
        assertThat(redeploy.step("deploy-rc", "Verify RC public ingress").get("run"))
                .isEqualTo("./scripts/deploy/verify-rc-ingress.sh");
        assertThat(redeploy.step("deploy-rc", "Remove RC SSH material").get("if")).isEqualTo("always()");
    }

    @Test
    void noProductionOrAlternateDeploymentAuthorityIsPresent() {
        assertThat(automatic.triggers()).containsOnlyKeys("workflow_run");
        Map<String, Object> trigger = WorkflowDocument.map(automatic.triggers().get("workflow_run"));
        assertThat(WorkflowDocument.list(trigger.get("workflows"))).containsExactly("Delivery");
        assertThat(WorkflowDocument.list(trigger.get("branches"))).containsExactly("master");
        assertThat(WorkflowDocument.list(trigger.get("types"))).containsExactly("completed");
        assertThat(redeploy.root().toString()).doesNotContain("workflow_run", "pull_request", "build-push-action",
                "setup-buildx-action", "upload-artifact", "download-artifact", "docker build", "docker push",
                "bootJar", "gradlew", "latest", "verify-delivery-handoff.sh", "docker compose", "sudo ");
        for (Object value : redeploy.steps("deploy-rc")) {
            Map<String, Object> step = WorkflowDocument.map(value);
            if (step.get("run") instanceof String run) {
                assertThat(run).doesNotContain("docker ", "docker compose", "ssh ", "sudo ", "runtimectl");
            }
        }
    }
}

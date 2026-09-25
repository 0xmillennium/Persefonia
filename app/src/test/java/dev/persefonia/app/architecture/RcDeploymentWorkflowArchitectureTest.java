package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RcDeploymentWorkflowArchitectureTest {
    private static final Path WORKFLOW = Path.of("../.github/workflows/deploy-rc.yml");

    @Test
    void workflowAcceptsOnlyCurrentTrustedDeliveryCompletion() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow).startsWith("name: Deploy RC\n")
                .contains("  workflow_run:\n    workflows:\n      - Delivery\n    branches:\n      - master\n    types:\n      - completed")
                .doesNotContain("  push:", "  pull_request:", "  workflow_dispatch:");
        assertThat(workflow)
                .contains("github.event.workflow_run.conclusion == 'success'")
                .contains("github.event.workflow_run.event == 'workflow_run'")
                .contains("github.event.workflow_run.head_branch == 'master'")
                .contains("github.event.workflow_run.head_repository.full_name == github.repository")
                .contains("github.event.workflow_run.head_repository.fork == false")
                .contains("github.sha == github.event.workflow_run.head_sha")
                .contains("group: rc-deployment\n  cancel-in-progress: false")
                .doesNotContain("group: rc-deployment-${{");
    }

    @Test
    void workflowUsesExactSourceAndLeastPrivilege() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        int permissionStart = workflow.indexOf("    permissions:\n");
        int permissionEnd = workflow.indexOf("    outputs:\n", permissionStart);
        assertThat(permissionStart).isPositive();
        assertThat(permissionEnd).isGreaterThan(permissionStart);
        assertThat(workflow.substring(permissionStart, permissionEnd).lines()
                .map(String::trim).filter(line -> !line.isEmpty()).toList())
                .containsExactlyInAnyOrder("permissions:", "contents: read", "packages: read", "actions: read");
        assertThat(workflow)
                .contains("permissions: {}")
                .contains("ref: ${{ github.event.workflow_run.head_sha }}")
                .contains("persist-credentials: false")
                .contains("test \"$(git rev-parse HEAD)\" = \"$EXPECTED_SOURCE_SHA\"")
                .contains("registry: ghcr.io")
                .contains("username: ${{ github.actor }}")
                .contains("password: ${{ github.token }}")
                .doesNotContain("environment:", "attestations: read", "attestations: write",
                        "id-token: write", "contents: write", "packages: write");
        assertThat(Pattern.compile("(?m)^        uses: ([^\\s#]+)").matcher(workflow).results()
                .map(match -> match.group(1)).toList())
                .containsExactly(
                        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                        "docker/login-action@dbcb813823bdd20940b903addbd779551569679f",
                        "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c");
    }

    @Test
    void workflowDelegatesArtifactIdentityAndExposesVerifiedOutputs() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        String resolutionStep = workflow.substring(workflow.indexOf("      - name: Resolve and verify qualified artifact"));
        String downloadAndVerification = workflow.substring(
                workflow.indexOf("      - name: Download triggering Delivery handoff"),
                workflow.indexOf("      - name: Resolve and verify qualified artifact"));

        assertThat(downloadAndVerification)
                .contains("name: persefonia-delivery-handoff-${{ github.event.workflow_run.id }}-${{ github.event.workflow_run.run_attempt }}")
                .contains("run-id: ${{ github.event.workflow_run.id }}")
                .contains("repository: ${{ github.repository }}")
                .contains("github-token: ${{ github.token }}")
                .contains("./scripts/deploy/verify-delivery-handoff.sh")
                .contains("\"$EXPECTED_SOURCE_SHA\" \"$DELIVERY_RUN_ID\" \"$DELIVERY_RUN_ATTEMPT\"")
                .contains("\"$GITHUB_REPOSITORY\" >> \"$GITHUB_OUTPUT\"");

        assertThat(resolutionStep)
                .contains("GH_TOKEN: ${{ github.token }}")
                .contains("EXPECTED_IMAGE_DIGEST: ${{ steps.handoff.outputs.expected_image_digest }}")
                .contains("run: ./scripts/deploy/resolve-qualified-artifact.sh \"$EXPECTED_SOURCE_SHA\" \"$EXPECTED_IMAGE_DIGEST\" \"$GITHUB_REPOSITORY\" docker/supported-platforms.txt >> \"$GITHUB_OUTPUT\"")
                .doesNotContain("image_name=", "image_reference=", "source_alias=", "printf ");
        assertThat(workflow.split(Pattern.quote("GH_TOKEN: ${{ github.token }}"), -1)).hasSize(2);
        for (String output : new String[] {
            "source_sha", "image_name", "image_digest", "image_reference", "source_alias"
        }) {
            assertThat(workflow).contains(output + ": ${{ steps.artifact.outputs." + output + " }}");
        }
        String jobs = workflow.substring(workflow.indexOf("\njobs:\n") + "\njobs:\n".length());
        assertThat(Pattern.compile("(?m)^  ([a-z][a-z-]+):\\s*$").matcher(jobs).results()
                .map(match -> match.group(1)).toList())
                .containsExactly("resolve-qualified-artifact");
    }

    @Test
    void workflowHasNoBuildOrRuntimeMutation() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow).doesNotContain(
                "./gradlew", "setup-java", "setup-gradle", "setup-node", "npm", "vite",
                "docker build", "docker compose", "docker pull", "ssh ", "scp ", "rsync ",
                "sftp ", "systemctl", "actions/upload-artifact");
    }
}

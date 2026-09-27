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
        assertThat(qualifiedJob(workflow))
                .contains("permissions: {}")
                .contains("ref: ${{ github.event.workflow_run.head_sha }}")
                .contains("persist-credentials: false")
                .contains("test \"$(git rev-parse HEAD)\" = \"$EXPECTED_SOURCE_SHA\"")
                .contains("registry: ghcr.io")
                .contains("username: ${{ github.actor }}")
                .contains("password: ${{ github.token }}")
                .doesNotContain("environment:", "attestations: read", "attestations: write",
                        "id-token: write", "contents: write", "packages: write");
        assertThat(Pattern.compile("(?m)^        uses: ([^\\s#]+)").matcher(qualifiedJob(workflow)).results()
                .map(match -> match.group(1)).toList())
                .containsExactly(
                        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                        "docker/login-action@dbcb813823bdd20940b903addbd779551569679f",
                        "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c");
    }

    @Test
    void workflowDelegatesArtifactIdentityAndExposesVerifiedOutputs() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        String resolutionStep = qualifiedJob(workflow).substring(
                qualifiedJob(workflow).indexOf("      - name: Resolve and verify qualified artifact"));
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
                .containsExactly("resolve-qualified-artifact", "deploy-rc");
    }

    @Test
    void workflowDelegatesDeploymentPolicyToRepositoryScripts() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow).doesNotContain(
                "./gradlew", "setup-java", "setup-gradle", "setup-node", "npm", "vite",
                "docker build", "docker compose", "docker pull", "docker run", "docker restart",
                "ssh ", "scp ", "rsync ", "sftp ", "sudo ", "systemctl",
                "git pull", "actions/upload-artifact", "POSTGRES_PASSWORD", "REDIS_PASSWORD",
                "OIDC_CLIENT_SECRET", "CLOUDFLARE_API_TOKEN");
    }

    @Test
    void deploymentJobEntersRcEnvironmentAfterQualificationWithOnlyCheckoutPermission() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        String trust = deployJob(workflow);

        assertThat(workflow).contains("\npermissions: {}\n");
        assertThat(trust)
                .contains("name: Deploy RC", "needs: resolve-qualified-artifact",
                        "runs-on: ubuntu-24.04", "environment:\n      name: rc\n",
                        "ref: ${{ needs.resolve-qualified-artifact.outputs.source_sha }}",
                        "EXPECTED_SOURCE_SHA: ${{ needs.resolve-qualified-artifact.outputs.source_sha }}",
                        "test \"$(git rev-parse HEAD)\" = \"$EXPECTED_SOURCE_SHA\"",
                        "persist-credentials: false",
                        "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1")
                .doesNotContain("deployment: false", "url:", "docker/", "docker ", "compose", "scp ", "rsync ",
                        "actions/download-artifact", "gh attestation", "resolve-qualified-artifact.sh",
                        "verify-delivery-handoff.sh", "GITHUB_ENV", "sudo ");
        assertThat(trust.substring(trust.indexOf("    permissions:\n"), trust.indexOf("    steps:\n"))
                .lines().map(String::trim).filter(line -> !line.isEmpty()).toList())
                .containsExactly("permissions:", "contents: read");
    }

    @Test
    void deploymentJobOrdersTrustMutationIngressSummaryAndCleanup() throws Exception {
        String trust = deployJob(Files.readString(WORKFLOW));
        String materialize = step(trust, "Materialize RC SSH private key", "Verify RC SSH target");
        String verify = step(trust, "Verify RC SSH target", "Deploy qualified artifact");
        String deploy = step(trust, "Deploy qualified artifact", "Verify RC public ingress");
        String ingress = step(trust, "Verify RC public ingress", "Write RC deployment summary");
        String summary = step(trust, "Write RC deployment summary", "Remove RC SSH material");
        String cleanup = trust.substring(trust.indexOf("      - name: Remove RC SSH material"));

        assertThat(trust.split(Pattern.quote("${{ secrets.RC_SSH_PRIVATE_KEY }}"), -1)).hasSize(2);
        assertThat(materialize).contains("RC_SSH_PRIVATE_KEY: ${{ secrets.RC_SSH_PRIVATE_KEY }}",
                "test -n \"$RC_SSH_PRIVATE_KEY\"", "umask 077",
                "\"$RUNNER_TEMP/persefonia-rc-ssh-key\"", "chmod 600");
        assertThat(verify).contains("vars.RC_SSH_HOST", "vars.RC_SSH_PORT", "vars.RC_SSH_USER",
                "vars.RC_SSH_HOST_KEY_SHA256", "./scripts/deploy/verify-ssh-target.sh",
                "\"$RC_SSH_HOST\"", "\"$RC_SSH_PORT\"", "\"$RC_SSH_USER\"",
                "\"$RC_SSH_HOST_KEY_SHA256\"", "\"$RUNNER_TEMP/persefonia-rc-ssh-key\"",
                "\"$RUNNER_TEMP/persefonia-rc-known-hosts\"")
                .doesNotContain("secrets.", "ssh-keyscan", "ssh-keygen", "ssh -");
        assertThat(deploy).contains("id: deployment", "./scripts/deploy/run-rc-deployment.sh",
                "needs.resolve-qualified-artifact.outputs.source_sha",
                "needs.resolve-qualified-artifact.outputs.image_reference",
                "\"$RUNNER_TEMP/persefonia-rc-known-hosts\"",
                "\"$QUALIFIED_SOURCE_SHA\" \"$QUALIFIED_IMAGE_REFERENCE\" >> \"$GITHUB_OUTPUT\"")
                .doesNotContain("source_alias", "secrets.");
        assertThat(ingress).contains("./scripts/deploy/verify-rc-ingress.sh");
        assertThat(summary).contains("steps.deployment.outputs.app_health", "GITHUB_STEP_SUMMARY")
                .doesNotContain("secrets.");
        assertThat(cleanup).contains("if: always()", "rm -f -- \"$RUNNER_TEMP/persefonia-rc-ssh-key\" \"$RUNNER_TEMP/persefonia-rc-known-hosts\"")
                .doesNotContain("secrets.", "rm -rf", "*");
        assertThat(trust.substring(0, trust.indexOf("      - name: Materialize RC SSH private key")))
                .doesNotContain("secrets.");
    }

    private static String qualifiedJob(String workflow) {
        return workflow.substring(0, workflow.indexOf("\n  deploy-rc:"));
    }

    private static String deployJob(String workflow) {
        return workflow.substring(workflow.indexOf("\n  deploy-rc:"));
    }

    private static String step(String job, String start, String next) {
        return job.substring(job.indexOf("      - name: " + start), job.indexOf("      - name: " + next));
    }
}

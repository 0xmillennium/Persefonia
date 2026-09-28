package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryWorkflowContractTest {
    private final WorkflowDocument workflow = WorkflowDocument.open("delivery.yml");

    DeliveryWorkflowContractTest() throws IOException {}

    @Test
    void acceptsOnlyTheSuccessfulTrustedCiSource() {
        Map<String, Object> trigger = WorkflowDocument.map(workflow.triggers().get("workflow_run"));
        assertThat(workflow.triggers()).containsOnlyKeys("workflow_run");
        assertThat(WorkflowDocument.list(trigger.get("workflows"))).containsExactly("CI");
        assertThat(WorkflowDocument.list(trigger.get("branches"))).containsExactly("master");
        assertThat(WorkflowDocument.list(trigger.get("types"))).containsExactly("completed");
        String guard = (String) workflow.job("publish-candidate").get("if");
        assertThat(guard).contains("conclusion == 'success'", "event == 'push'", "head_branch == 'master'",
                "head_repository.full_name == github.repository", "head_repository.fork == false",
                "github.sha == github.event.workflow_run.head_sha");
        assertThat(WorkflowDocument.map(workflow.step("publish-candidate", "Check out source").get("with")))
                .containsEntry("ref", "${{ github.event.workflow_run.head_sha }}");
        Map<String, Object> concurrency = WorkflowDocument.map(workflow.root().get("concurrency"));
        assertThat(concurrency).containsEntry("group", "delivery-${{ github.event.workflow_run.head_sha }}")
                .containsEntry("cancel-in-progress", false);
    }

    @Test
    void consumesTheVerifiedCiBootJarAndPublishesQualifiedArtifact() throws IOException {
        Map<String, Object> download = workflow.step("publish-candidate", "Download verified BootJar");
        assertThat((String) download.get("uses")).startsWith("actions/download-artifact@");
        assertThat(WorkflowDocument.map(download.get("with")))
                .containsEntry("run-id", "${{ github.event.workflow_run.id }}")
                .containsEntry("name", "persefonia-bootjar-${{ github.event.workflow_run.head_sha }}");
        for (Object jobValue : workflow.jobs().values()) {
            Map<String, Object> job = WorkflowDocument.map(jobValue);
            for (Object stepValue : WorkflowDocument.list(job.get("steps"))) {
                Map<String, Object> step = WorkflowDocument.map(stepValue);
                if (step.get("uses") instanceof String uses) {
                    assertThat(uses).doesNotContain("setup-java", "setup-node");
                }
                if (step.get("run") instanceof String run) {
                    assertThat(run).doesNotContain("./gradlew", "gradle ");
                }
            }
        }
        Map<String, Object> build = WorkflowDocument.map(workflow.step("publish-candidate", "Publish digest-only candidate").get("with"));
        List<String> platforms = Files.readAllLines(Path.of("../docker/supported-platforms.txt")).stream()
                .map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        assertThat(List.of(((String) build.get("platforms")).split(","))).containsExactlyInAnyOrderElementsOf(platforms);
        Map<String, Object> strategy = WorkflowDocument.map(workflow.job("verify-candidate").get("strategy"));
        Map<String, Object> matrix = WorkflowDocument.map(strategy.get("matrix"));
        List<String> nativePlatforms = WorkflowDocument.list(matrix.get("include")).stream()
                .map(WorkflowDocument::map).map(item -> (String) item.get("platform")).toList();
        assertThat(nativePlatforms).containsExactlyInAnyOrderElementsOf(platforms);
        assertThat(WorkflowDocument.list(matrix.get("include")).stream().map(WorkflowDocument::map)
                .map(item -> item.get("platform") + "@" + item.get("runner")).toList())
                .contains("linux/amd64@ubuntu-24.04", "linux/arm64@ubuntu-24.04-arm");
        assertThat((String) build.get("outputs")).contains("push-by-digest=true", "name-canonical=true");
        assertThat(build).containsEntry("sbom", true).containsEntry("provenance", "mode=max,version=v1");
        assertThat((String) workflow.step("verify-candidate", "Verify registry artifact and supply-chain evidence").get("run"))
                .contains("verify-container-image.sh");
        assertThat((String) workflow.step("verify-candidate", "Run native candidate smoke").get("run"))
                .contains("smoke-container-image.sh");
    }

    @Test
    void handoffWaitsForAllQualificationAndAliasMutation() {
        assertCompleteHandoffNeeds(workflow);
        assertThat(WorkflowDocument.list(workflow.job("publish-source-alias").get("needs")))
                .contains("publish-candidate", "verify-candidate");
        assertThat((String) workflow.step("publish-source-alias", "Publish write-once source alias").get("run"))
                .contains("publish-source-alias.sh");
        assertThat((String) workflow.step("publish-deployment-handoff", "Write deployment handoff").get("run"))
                .contains("write-delivery-handoff.sh");
        assertThat(WorkflowDocument.map(workflow.step("publish-deployment-handoff", "Upload deployment handoff").get("with")))
                .containsEntry("overwrite", false)
                .containsEntry("name", "persefonia-delivery-handoff-${{ github.run_id }}-${{ github.run_attempt }}")
                .containsEntry("if-no-files-found", "error");
        assertThat(WorkflowDocument.map(workflow.step("publish-deployment-handoff", "Check out Delivery source").get("with")))
                .containsEntry("ref", "${{ needs.publish-candidate.outputs.source_sha }}");
    }

    @Test
    void newlyAddedQualificationJobMustBlockHandoff() throws IOException {
        WorkflowDocument changed = WorkflowDocument.open("delivery.yml");
        changed.jobs().put("extra-qualification", Map.of("runs-on", "ubuntu-24.04"));
        assertThatThrownBy(() -> assertCompleteHandoffNeeds(changed))
                .isInstanceOf(AssertionError.class);
        WorkflowDocument.list(changed.job("publish-deployment-handoff").get("needs"))
                .add("extra-qualification");
        assertCompleteHandoffNeeds(changed);
    }

    private static void assertCompleteHandoffNeeds(WorkflowDocument delivery) {
        Set<String> predecessors = new HashSet<>(delivery.jobs().keySet());
        predecessors.remove("publish-deployment-handoff");
        assertThat(WorkflowDocument.list(delivery.job("publish-deployment-handoff").get("needs")))
                .containsExactlyInAnyOrderElementsOf(predecessors);
    }

    @Test
    void allBuildxJobsVerifyTheEffectivePinnedBuilder() {
        Map<String, Object> environment = WorkflowDocument.map(workflow.root().get("env"));
        assertThat(environment).containsKeys("BUILDX_VERSION", "BUILDKIT_VERSION", "BUILDKIT_IMAGE");
        assertThat((String) environment.get("BUILDKIT_IMAGE")).matches("moby/buildkit@sha256:[0-9a-f]{64}");
        for (String jobId : List.of("publish-candidate", "verify-candidate", "publish-source-alias")) {
            Map<String, Object> setup = workflow.step(jobId, "Set up Docker Buildx");
            assertThat((String) setup.get("uses")).startsWith("docker/setup-buildx-action@");
            assertThat(WorkflowDocument.map(setup.get("with")))
                    .containsEntry("version", "${{ env.BUILDX_VERSION }}");
            assertThat((String) WorkflowDocument.map(setup.get("with")).get("driver-opts"))
                    .contains("image=${{ env.BUILDKIT_IMAGE }}");
            Map<String, Object> verification = workflow.step(jobId, "Verify effective delivery toolchain");
            assertThat(WorkflowDocument.map(verification.get("env")))
                    .containsEntry("BUILDER_NODES", "${{ steps.buildx.outputs.nodes }}");
            assertThat((String) verification.get("run")).contains("verify-delivery-toolchain.sh");
        }
    }
}

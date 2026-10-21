package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineHandoffWorkflowContractTest {
    @Test
    void ciBootJarProducerMatchesDeliveryConsumer() throws IOException {
        WorkflowDocument ci = WorkflowDocument.open("ci.yml");
        WorkflowDocument delivery = WorkflowDocument.open("delivery.yml");
        assertBootJarHandoff(ci, delivery);

        WorkflowDocument changed = WorkflowDocument.open("ci.yml");
        WorkflowDocument.map(changed.step("build-jvm", "Upload verified BootJar").get("with"))
                .put("name", "persefonia-bootjar-latest");
        assertThatThrownBy(() -> assertBootJarHandoff(changed, delivery)).isInstanceOf(AssertionError.class);
    }

    @Test
    void deliveryHandoffProducerMatchesTriggeringDeployConsumer() throws IOException {
        WorkflowDocument delivery = WorkflowDocument.open("delivery.yml");
        WorkflowDocument deploy = WorkflowDocument.open("deploy-rc.yml");
        assertDeliveryHandoff(delivery, deploy);

        WorkflowDocument changed = WorkflowDocument.open("delivery.yml");
        WorkflowDocument.map(changed.step("publish-deployment-handoff", "Upload deployment handoff").get("with"))
                .put("name", "persefonia-delivery-handoff-latest");
        assertThatThrownBy(() -> assertDeliveryHandoff(changed, deploy)).isInstanceOf(AssertionError.class);
    }

    @Test
    void equivalentMasterCandidateUsesTheSameHandoffOnlyAfterTrustedComparison() throws IOException {
        WorkflowDocument ci = WorkflowDocument.open("ci.yml");
        assertThat(ci.job("compare").get("if")).isEqualTo("needs.eligibility.outputs.eligible == 'true'");
        assertThat(WorkflowDocument.list(ci.job("compare").get("needs")))
                .containsExactlyInAnyOrder("automation", "eligibility");
        for (var names : Map.of("Upload equivalent master BootJar", "Upload verified BootJar",
                "Upload equivalent master browser input", "Upload browser qualification input").entrySet()) {
            var compared = ci.step("compare", names.getKey());
            var executed = ci.step("build-jvm", names.getValue());
            assertThat(compared.get("if")).isEqualTo("steps.compare.outputs.reuse == 'true'");
            assertThat(WorkflowDocument.map(compared.get("with")))
                    .isEqualTo(WorkflowDocument.map(executed.get("with")));
        }
        WorkflowDocument delivery = WorkflowDocument.open("delivery.yml");
        var download = WorkflowDocument.map(delivery.step("publish-candidate", "Download master qualification record").get("with"));
        assertThat(download).containsEntry("name", "persefonia-qualification-${{ github.event.workflow_run.head_sha }}")
                .containsEntry("run-id", "${{ github.event.workflow_run.id }}")
                .containsEntry("repository", "${{ github.repository }}");
        assertThat(delivery.step("publish-candidate", "Validate trusted master qualification").get("run"))
                .asString().contains("scripts/ci/delivery-admission.py");
        var names = delivery.steps("publish-candidate").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")).toList();
        assertThat(names).containsSubsequence("Download verified BootJar", "Download master qualification record",
                "Validate trusted master qualification", "Verify and stage CI BootJar", "Log in to GitHub Container Registry");
    }

    private static void assertBootJarHandoff(WorkflowDocument ci, WorkflowDocument delivery) {
        Map<String, Object> producer = ci.step("build-jvm", "Upload verified BootJar");
        assertThat((String) producer.get("uses")).startsWith("actions/upload-artifact@");
        assertThat(producer.get("if")).isEqualTo("github.event_name == 'push' && github.ref == 'refs/heads/master'");
        Map<String, Object> upload = WorkflowDocument.map(producer.get("with"));
        assertThat(upload).containsEntry("name", "persefonia-bootjar-${{ github.sha }}")
                .containsEntry("if-no-files-found", "error");
        assertThat(((String) upload.get("path")).lines().toList())
                .containsExactly("app/build/ci/persefonia.jar", "app/build/ci/persefonia.jar.sha256", "app/build/ci/source-sha.txt");
        assertThat(ci.step("build-jvm", "Verify BootJar").get("run"))
                .isEqualTo("""
                        ./scripts/ci/verify-bootjar.sh app/build/libs/persefonia.jar app/build/ci
                        printf '%s\\n' "$GITHUB_SHA" > app/build/ci/source-sha.txt
                        """);

        Map<String, Object> download = delivery.step("publish-candidate", "Download verified BootJar");
        assertThat((String) download.get("uses")).startsWith("actions/download-artifact@");
        Map<String, Object> consumer = WorkflowDocument.map(download.get("with"));
        assertThat(consumer).containsEntry("name", ((String) upload.get("name"))
                        .replace("github.sha", "github.event.workflow_run.head_sha"))
                .containsEntry("run-id", "${{ github.event.workflow_run.id }}")
                .containsEntry("repository", "${{ github.repository }}")
                .containsEntry("github-token", "${{ github.token }}")
                .containsEntry("path", "delivery-input");
        String verification = (String) delivery.step("publish-candidate", "Verify and stage CI BootJar").get("run");
        assertThat(verification).contains("cd delivery-input", "sha256sum --check persefonia.jar.sha256",
                "./scripts/ci/verify-bootjar.sh delivery-input/persefonia.jar app/build/libs");
        assertThat(verification.indexOf("sha256sum --check persefonia.jar.sha256"))
                .isLessThan(verification.indexOf("./scripts/ci/verify-bootjar.sh"));
    }

    private static void assertDeliveryHandoff(WorkflowDocument delivery, WorkflowDocument deploy) {
        Map<String, Object> writer = delivery.step("publish-deployment-handoff", "Write deployment handoff");
        assertThat(WorkflowDocument.map(writer.get("env"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DELIVERY_SOURCE_SHA", "${{ needs.publish-candidate.outputs.source_sha }}",
                "DELIVERY_IMAGE_NAME", "${{ needs.publish-candidate.outputs.image_name }}",
                "DELIVERY_IMAGE_DIGEST", "${{ needs.publish-candidate.outputs.image_digest }}"));
        assertThat((String) writer.get("run")).contains("./scripts/release/write-delivery-handoff.sh",
                "delivery-handoff/delivery-handoff.txt",
                "\"$DELIVERY_SOURCE_SHA\" \"$DELIVERY_IMAGE_NAME\" \"$DELIVERY_IMAGE_DIGEST\"",
                "\"$GITHUB_RUN_ID\" \"$GITHUB_RUN_ATTEMPT\"");
        Map<String, Object> uploadStep = delivery.step("publish-deployment-handoff", "Upload deployment handoff");
        assertThat((String) uploadStep.get("uses")).startsWith("actions/upload-artifact@");
        Map<String, Object> upload = WorkflowDocument.map(uploadStep.get("with"));
        assertThat(upload).containsEntry("name", "persefonia-delivery-handoff-${{ github.run_id }}-${{ github.run_attempt }}")
                .containsEntry("path", "delivery-handoff/delivery-handoff.txt")
                .containsEntry("if-no-files-found", "error")
                .containsEntry("overwrite", false);

        Map<String, Object> downloadStep = deploy.step("resolve-qualified-artifact", "Download triggering Delivery handoff");
        assertThat((String) downloadStep.get("uses")).startsWith("actions/download-artifact@");
        Map<String, Object> download = WorkflowDocument.map(downloadStep.get("with"));
        String expectedName = ((String) upload.get("name"))
                .replace("github.run_id", "github.event.workflow_run.id")
                .replace("github.run_attempt", "github.event.workflow_run.run_attempt");
        assertThat(download).containsEntry("name", expectedName)
                .containsEntry("run-id", "${{ github.event.workflow_run.id }}")
                .containsEntry("repository", "${{ github.repository }}")
                .containsEntry("github-token", "${{ github.token }}")
                .containsEntry("path", "delivery-handoff");
        assertThat((String) deploy.step("resolve-qualified-artifact", "Verify triggering Delivery handoff").get("run"))
                .contains("delivery-handoff/delivery-handoff.txt", "\"$GITHUB_REPOSITORY\" >> \"$GITHUB_OUTPUT\"");
    }
}

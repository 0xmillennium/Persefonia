package dev.persefonia.automation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WorkflowSecurityPolicyTest {
    private static final Pattern ACTION = Pattern.compile("([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?)@([0-9a-f]{40})");
    private static final Set<String> TRUSTED_ACTIONS = Set.of(
            "actions/checkout", "actions/setup-java", "actions/setup-node", "actions/upload-artifact",
            "actions/download-artifact", "actions/attest", "gradle/actions/setup-gradle",
            "docker/setup-buildx-action", "docker/login-action", "docker/build-push-action");
    private static final Set<String> READ_PERMISSIONS = Set.of("contents", "actions", "packages");
    private static final Set<String> WRITE_PERMISSIONS = Set.of("packages", "attestations", "id-token");

    @Test
    void everyTrackedWorkflowHasPinnedTrustedActionsAndBoundedJobs() throws IOException {
        try (var paths = Files.list(Path.of("../.github/workflows"))) {
            for (Path path : paths.filter(file -> file.toString().matches(".*\\.ya?ml")).toList()) {
                WorkflowDocument workflow = WorkflowDocument.open(path.getFileName().toString());
                assertThat(workflow.triggers()).doesNotContainKey("pull_request_target");
                assertThat(workflow.root()).containsKey("permissions");
                assertThat(WorkflowDocument.map(WorkflowDocument.map(workflow.root().get("defaults")).get("run")))
                        .containsEntry("shell", "bash");
                for (var entry : workflow.jobs().entrySet()) {
                    String jobId = entry.getKey();
                    var job = WorkflowDocument.map(entry.getValue());
                    assertThat(job).containsKeys("timeout-minutes", "permissions");
                    assertThat(((Number) job.get("timeout-minutes")).intValue()).isBetween(1, 30);
                    var permissions = WorkflowDocument.map(job.get("permissions"));
                    for (var permission : permissions.entrySet()) {
                        String name = permission.getKey();
                        String level = (String) permission.getValue();
                        assertThat(level).isIn("read", "write", "none");
                        if (level.equals("write")) {
                            assertThat(name).isIn(WRITE_PERMISSIONS);
                            assertThat(workflow.root().get("name")).isEqualTo("Delivery");
                            assertThat(jobId).isIn("publish-candidate", "publish-source-alias");
                        } else if (level.equals("read")) {
                            assertThat(name).isIn(READ_PERMISSIONS);
                        }
                    }
                    for (Object stepValue : workflow.steps(jobId)) {
                        var step = WorkflowDocument.map(stepValue);
                        Object uses = step.get("uses");
                        if (uses != null) {
                            Matcher matcher = ACTION.matcher((String) uses);
                            assertThat(matcher.matches()).as(path + " " + jobId + " " + step.get("name")).isTrue();
                            assertThat(matcher.group(1)).isIn(TRUSTED_ACTIONS);
                            if (matcher.group(1).equals("actions/checkout")) {
                                assertThat(WorkflowDocument.map(step.get("with")))
                                        .containsEntry("persist-credentials", false);
                            }
                        }
                        if (step.containsKey("shell")) {
                            assertThat(step.get("shell")).isIn("bash", "sh");
                        }
                        if (!jobId.equals("deploy-rc")) {
                            assertThat(step.toString()).doesNotContain("secrets.");
                        }
                    }
                }
            }
        }
    }
}

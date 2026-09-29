package dev.persefonia.automation.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.WorkflowDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WorkflowSecurityPolicyTest {
    private static final Map<String, String> ALLOWED_SECRETS = Map.of(
            "deploy-rc.yml/jobs/deploy-rc/steps/Materialize RC SSH private key/env/RC_SSH_PRIVATE_KEY",
            "${{ secrets.RC_SSH_PRIVATE_KEY }}",
            "redeploy-rc.yml/jobs/deploy-rc/steps/Materialize RC SSH private key/env/RC_SSH_PRIVATE_KEY",
            "${{ secrets.RC_SSH_PRIVATE_KEY }}");
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
                assertSecretsAllowed(path.getFileName().toString(), workflow.root());
                assertThat(workflow.triggers()).doesNotContainKey("pull_request_target");
                assertThat(workflow.root()).containsKey("permissions");
                assertThat(WorkflowDocument.map(WorkflowDocument.map(workflow.root().get("defaults")).get("run")))
                        .containsEntry("shell", "bash");
                for (var entry : workflow.jobs().entrySet()) {
                    String jobId = entry.getKey();
                    var job = WorkflowDocument.map(entry.getValue());
                    assertNoJobLevelUses(path.getFileName().toString(), jobId, job);
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
                        assertPinnedTrustedStepAction(path.getFileName().toString(), jobId, step);
                        if (step.containsKey("shell")) {
                            assertThat(step.get("shell")).isIn("bash", "sh");
                        }
                    }
                }
            }
        }
    }

    @Test
    void rejectsSecretsAtEveryUnauthorizedStructuralScope() throws IOException {
        for (String scope : List.of("workflow", "job", "step", "with", "qualification", "private-key-outside", "duplicate-name", "mapping-key")) {
            WorkflowDocument workflow = WorkflowDocument.open("deploy-rc.yml");
            Map<String, Object> root = workflow.root();
            String expected;
            switch (scope) {
                case "workflow" -> { root.put("env", Map.of("BROAD", "${{ secrets.NEW_SECRET }}")); expected = "deploy-rc.yml/env/BROAD"; }
                case "job" -> { workflow.job("deploy-rc").put("env", Map.of("BROAD", "${{ secrets.NEW_SECRET }}")); expected = "deploy-rc.yml/jobs/deploy-rc/env/BROAD"; }
                case "step" -> { workflow.step("deploy-rc", "Verify RC SSH target").put("env", Map.of("BROAD", "${{ secrets.NEW_SECRET }}")); expected = "deploy-rc.yml/jobs/deploy-rc/steps/Verify RC SSH target/env/BROAD"; }
                case "with" -> { workflow.step("deploy-rc", "Check out qualified source").put("with", Map.of("ref", "prefix-${{ secrets.NEW_SECRET }}-suffix")); expected = "deploy-rc.yml/jobs/deploy-rc/steps/Check out qualified source/with/ref"; }
                case "qualification" -> { workflow.job("resolve-qualified-artifact").put("env", Map.of("BROAD", "${{ secrets.NEW_SECRET }}")); expected = "deploy-rc.yml/jobs/resolve-qualified-artifact/env/BROAD"; }
                case "private-key-outside" -> { workflow.step("deploy-rc", "Verify RC SSH target").put("env", Map.of("RC_SSH_PRIVATE_KEY", "${{ secrets.RC_SSH_PRIVATE_KEY }}")); expected = "deploy-rc.yml/jobs/deploy-rc/steps/Verify RC SSH target/env/RC_SSH_PRIVATE_KEY"; }
                case "duplicate-name" -> { workflow.steps("deploy-rc").add(Map.of("name", "Materialize RC SSH private key", "env", Map.of("RC_SSH_PRIVATE_KEY", "${{ secrets.RC_SSH_PRIVATE_KEY }}"))); expected = "deploy-rc.yml/jobs/deploy-rc/steps/Materialize RC SSH private key/env/RC_SSH_PRIVATE_KEY"; }
                case "mapping-key" -> { root.put("${{ secrets.NEW_SECRET }}", "value"); expected = "deploy-rc.yml/${{ secrets.NEW_SECRET }}"; }
                default -> throw new IllegalStateException(scope);
            }
            assertThat(unauthorizedSecrets("deploy-rc.yml", root)).as(scope).containsKey(expected);
        }
    }

    @Test
    void detectsWholeAndIndexedSecretContextsOnlyInsideExpressions() throws IOException {
        for (String expression : List.of("${{ secrets.FOO }}", "${{ secrets[\"FOO\"] }}",
                "${{ secrets['FOO'] }}", "${{ secrets }}", "${{ toJSON(secrets) }}",
                "prefix-${{ secrets.FOO }}-suffix", "${{ condition && secrets.FOO }}",
                "${{ condition && toJSON(secrets) }}",
                "${{ format('{{Hello {0}!}}', secrets.FOO) }}",
                "${{ format('it''s {{literal}} {0}', secrets.FOO) }}")) {
            WorkflowDocument workflow = WorkflowDocument.open("deploy-rc.yml");
            workflow.root().put("env", Map.of("EXPOSED", expression));
            assertThat(unauthorizedSecrets("deploy-rc.yml", workflow.root()))
                    .as(expression).containsKey("deploy-rc.yml/env/EXPOSED");
        }
        for (String text : List.of("ordinary prose about secrets", "${{ github.token }}", "${{ 'secrets' }}",
                "${{ format('{{secrets}}', github.token) }}")) {
            WorkflowDocument workflow = WorkflowDocument.open("deploy-rc.yml");
            workflow.root().put("env", Map.of("SAFE", text));
            assertThat(unauthorizedSecrets("deploy-rc.yml", workflow.root())).as(text).isEmpty();
        }
    }

    @Test
    void rejectsJobLevelReusableWorkflowsRegardlessOfPin() throws IOException {
        for (String reference : List.of("owner/repo/.github/workflows/example.yml@main",
                "owner/repo/.github/workflows/example.yml@" + "a".repeat(40))) {
            WorkflowDocument workflow = WorkflowDocument.open("ci.yml");
            workflow.jobs().put("unexpected", Map.of("uses", reference));
            assertThatThrownBy(() -> assertNoJobLevelUses("ci.yml", "unexpected", workflow.job("unexpected")))
                    .as(reference).isInstanceOf(AssertionError.class).hasMessageContaining("ci.yml/jobs/unexpected/uses");
        }
    }

    @Test
    void rejectsMutableStepActionReference() throws IOException {
        WorkflowDocument workflow = WorkflowDocument.open("ci.yml");
        Map<String, Object> checkout = workflow.step("automation", "Check out repository");
        assertPinnedTrustedStepAction("ci.yml", "automation", checkout);
        checkout.put("uses", "actions/checkout@v7");
        assertThatThrownBy(() -> assertPinnedTrustedStepAction("ci.yml", "automation", checkout))
                .isInstanceOf(AssertionError.class);
    }

    private static void assertPinnedTrustedStepAction(String file, String jobId, Map<String, Object> step) {
        Object uses = step.get("uses");
        if (uses == null) return;
        Matcher matcher = ACTION.matcher((String) uses);
        assertThat(matcher.matches()).as(file + "/jobs/" + jobId + "/steps/" + step.get("name") + "/uses")
                .isTrue();
        assertThat(matcher.group(1)).isIn(TRUSTED_ACTIONS);
        if (matcher.group(1).equals("actions/checkout")) {
            assertThat(WorkflowDocument.map(step.get("with"))).containsEntry("persist-credentials", false);
        }
    }

    private static void assertNoJobLevelUses(String file, String jobId, Map<String, Object> job) {
        assertThat(job).as(file + "/jobs/" + jobId + "/uses").doesNotContainKey("uses");
    }

    private static void assertSecretsAllowed(String file, Map<String, Object> root) {
        assertThat(unauthorizedSecrets(file, root)).as("unauthorized secrets in " + file).isEmpty();
        if (file.equals("deploy-rc.yml") || file.equals("redeploy-rc.yml")) {
            assertThat(secretLocations(file, root)).containsEntry(
                    file + "/jobs/deploy-rc/steps/Materialize RC SSH private key/env/RC_SSH_PRIVATE_KEY",
                    List.of("${{ secrets.RC_SSH_PRIVATE_KEY }}"));
        }
    }

    private static Map<String, List<String>> unauthorizedSecrets(String file, Map<String, Object> root) {
        Map<String, List<String>> actual = secretLocations(file, root);
        actual.entrySet().removeIf(entry -> ALLOWED_SECRETS.containsKey(entry.getKey())
                && entry.getValue().equals(List.of(ALLOWED_SECRETS.get(entry.getKey()))));
        return actual;
    }

    private static Map<String, List<String>> secretLocations(String file, Object root) {
        Map<String, List<String>> locations = new LinkedHashMap<>();
        visit(root, file, locations);
        return locations;
    }

    private static void visit(Object node, String path, Map<String, List<String>> locations) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String child = path + "/" + key;
                if (key instanceof String text && referencesSecrets(text)) {
                    locations.computeIfAbsent(child, ignored -> new ArrayList<>()).add(text);
                }
                visit(value, child, locations);
            });
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                Object name = item instanceof Map<?, ?> map ? map.get("name") : null;
                visit(item, path + "/" + (name instanceof String ? name : i), locations);
            }
        } else if (node instanceof String value && referencesSecrets(value)) {
            locations.computeIfAbsent(path, ignored -> new ArrayList<>()).add(value);
        }
    }

    private static boolean referencesSecrets(String text) {
        int start = 0;
        while ((start = text.indexOf("${{", start)) >= 0) {
            char quote = 0;
            int index = start + 3;
            for (; index < text.length();) {
                char current = text.charAt(index);
                if (quote != 0) {
                    if (current == quote) {
                        if (quote == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                            index += 2;
                            continue;
                        }
                        quote = 0;
                    } else if (quote == '"' && current == '\\') {
                        index++;
                    }
                    index++;
                } else if (current == '\'' || current == '"') {
                    quote = current;
                    index++;
                } else if (current == '}' && index + 1 < text.length() && text.charAt(index + 1) == '}') {
                    index += 2;
                    break;
                } else if (Character.isLetter(current) || current == '_') {
                    int tokenStart = index++;
                    while (index < text.length()
                            && (Character.isLetterOrDigit(text.charAt(index))
                            || text.charAt(index) == '_')) index++;
                    if (text.substring(tokenStart, index).equals("secrets")) {
                        int previous = tokenStart - 1;
                        while (previous >= 0 && Character.isWhitespace(text.charAt(previous))) previous--;
                        if (previous < 0 || text.charAt(previous) != '.') return true;
                    }
                } else {
                    index++;
                }
            }
            start = index;
        }
        return false;
    }
}

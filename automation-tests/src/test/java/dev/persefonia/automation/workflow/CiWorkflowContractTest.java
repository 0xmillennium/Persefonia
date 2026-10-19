package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.WorkflowDocument;
import dev.persefonia.automation.support.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class CiWorkflowContractTest {
    private final WorkflowDocument workflow = WorkflowDocument.open("ci.yml");

    CiWorkflowContractTest() throws IOException {}

    @Test
    void acceptsOnlyMasterPullRequestsPushesAndManualDispatch() {
        assertCiTriggers(workflow);
        for (String unexpected : List.of("schedule", "pull_request_target", "repository_dispatch", "workflow_call")) {
            WorkflowDocument changed = openCi();
            changed.triggers().put(unexpected, Map.of());
            assertThatThrownBy(() -> assertCiTriggers(changed)).as(unexpected).isInstanceOf(AssertionError.class);
        }
        WorkflowDocument broadened = openCi();
        WorkflowDocument.map(broadened.triggers().get("push")).put("branches", List.of("master", "feature"));
        assertThatThrownBy(() -> assertCiTriggers(broadened)).isInstanceOf(AssertionError.class);
        WorkflowDocument tags = openCi();
        WorkflowDocument.map(tags.triggers().get("push")).put("tags", List.of("*"));
        assertThatThrownBy(() -> assertCiTriggers(tags)).isInstanceOf(AssertionError.class);
    }

    @Test
    void ciServicesRemainDigestPinned() {
        assertDigestPinnedServices(workflow);
        for (String mutable : List.of("postgres:17", "redis:latest", "redis@sha256:short")) {
            WorkflowDocument changed = openCi();
            WorkflowDocument.map(WorkflowDocument.map(changed.job("verify").get("services")).get("redis"))
                    .put("image", mutable);
            assertThatThrownBy(() -> assertDigestPinnedServices(changed)).as(mutable)
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void browserQualificationUsesTheCiPostgresImage() throws IOException {
        String ciImage = (String) WorkflowDocument.map(
                WorkflowDocument.map(workflow.job("verify").get("services")).get("postgres")).get("image");
        String script = Files.readString(Path.of("../scripts/browser/check.sh"));
        Matcher defaultImage = Pattern.compile("(?m)^postgres_image=\"\\$\\{POSTGRES_IMAGE:-([^}\\r\\n]+)}\"$")
                .matcher(script);
        assertThat(defaultImage.find()).isTrue();
        assertThat(defaultImage.group(1)).matches("[^\\s@]+@sha256:[0-9a-f]{64}").isEqualTo(ciImage);
        assertThat(script).contains("docker run", "\"$postgres_image\"");
    }

    @Test
    void localDependenciesUseTheAcceptedCiImages() throws IOException {
        Object localYaml = new Load(LoadSettings.builder().build())
                .loadFromString(Files.readString(Path.of("../compose.yaml")));
        Map<String, Object> localServices = WorkflowDocument.map(WorkflowDocument.map(localYaml).get("services"));
        Map<String, Object> ciServices = WorkflowDocument.map(workflow.job("verify").get("services"));
        for (String service : List.of("postgres", "redis")) {
            assertThat(WorkflowDocument.map(localServices.get(service)).get("image"))
                    .as(service).isEqualTo(WorkflowDocument.map(ciServices.get(service)).get("image"));
        }
    }

    @Test
    void verifyChecksTheLocalComposeContract() {
        assertThat(workflow.step("verify", "Validate local Compose contract").get("run"))
                .asString().contains("./scripts/ci/verify-local-compose.sh");
        try {
            Object localYaml = new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(Path.of("../compose.yaml")));
            String appImage = (String) WorkflowDocument.map(
                    WorkflowDocument.map(WorkflowDocument.map(localYaml).get("services")).get("app")).get("image");
            assertThat(workflow.step("verify", "Verify development toolchain platforms").get("run"))
                    .asString().contains("verify-image-platforms.sh", appImage, "supported-platforms.txt contains");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void ciConcurrencyAndRootPermissionsMatchAcceptedScope() {
        assertThat(WorkflowDocument.map(workflow.root().get("concurrency")))
                .containsEntry("group", "ci-${{ github.event.pull_request.number || github.sha }}")
                .containsEntry("cancel-in-progress", "${{ github.event_name == 'pull_request' }}");
        assertThat(WorkflowDocument.map(workflow.root().get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
    }

    private static WorkflowDocument openCi() {
        try {
            return WorkflowDocument.open("ci.yml");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertCiTriggers(WorkflowDocument ci) {
        assertThat(ci.triggers()).containsOnlyKeys("pull_request", "push", "workflow_dispatch");
        for (String event : List.of("pull_request", "push")) {
            assertThat(WorkflowDocument.map(ci.triggers().get(event)))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("branches", List.of("master")));
        }
        assertThat(ci.triggers().get("workflow_dispatch")).isNull();
    }

    private static void assertDigestPinnedServices(WorkflowDocument ci) {
        Map<String, Object> services = WorkflowDocument.map(ci.job("verify").get("services"));
        assertThat(services).containsOnlyKeys("postgres", "redis");
        for (var service : services.entrySet()) {
            assertThat((String) WorkflowDocument.map(service.getValue()).get("image"))
                    .as("verify/services/" + service.getKey() + "/image")
                    .matches("[^\\s@]+@sha256:[0-9a-f]{64}");
        }
    }

    @Test
    void automationFailsBeforeApplicationInfrastructureStarts() {
        assertThat(workflow.jobs()).containsOnlyKeys("automation", "verify", "browser", "browser-receipts", "gate");
        Map<String, Object> automation = workflow.job("automation");
        Map<String, Object> verify = workflow.job("verify");
        assertThat(WorkflowDocument.map(automation.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(WorkflowDocument.map(verify.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(automation).doesNotContainKeys("services");
        assertThat(automation.get("needs")).isNull();
        assertThat(verify.get("needs")).isEqualTo("automation");
        assertThat(workflow.job("browser").get("needs")).isEqualTo("verify");
        assertThat(workflow.job("browser")).doesNotContainKeys("services", "environment");
        assertThat(WorkflowDocument.map(workflow.job("browser").get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
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
    void browserMatrixRequiresTheVerifiedArtifactAndAllQualificationSteps() {
        assertBrowserQualification(workflow);
        for (String name : List.of("Download verified BootJar", "Stage verified browser artifact",
                "Install browser matrix", "Qualify production homepage and browser matrix")) {
            WorkflowDocument missing = openCi();
            WorkflowDocument.list(missing.job("browser").get("steps")).removeIf(step ->
                    name.equals(WorkflowDocument.map(step).get("name")));
            assertThatThrownBy(() -> assertBrowserQualification(missing)).isInstanceOf(AssertionError.class);
        }
        WorkflowDocument reordered = openCi();
        List<Object> steps = WorkflowDocument.list(reordered.job("browser").get("steps"));
        int browserIndex = stepIndex(steps, "Qualify production homepage and browser matrix");
        Collections.swap(steps, browserIndex, browserIndex - 1);
        assertThatThrownBy(() -> assertBrowserQualification(reordered)).isInstanceOf(AssertionError.class);
        WorkflowDocument bypassed = openCi();
        bypassed.step("browser", "Qualify production homepage and browser matrix")
                .put("run", "cd frontend && npm run test:browser");
        assertThatThrownBy(() -> assertBrowserQualification(bypassed)).isInstanceOf(AssertionError.class);
        WorkflowDocument missingViewport = openCi();
        WorkflowDocument.list(WorkflowDocument.map(WorkflowDocument.map(
                missingViewport.job("browser").get("strategy")).get("matrix")).get("include"))
                .removeIf(value -> "mobile-390".equals(WorkflowDocument.map(value).get("project")));
        assertThatThrownBy(() -> assertBrowserQualification(missingViewport)).isInstanceOf(AssertionError.class);
    }

    @Test
    void verifyRequiresProductionFrontendQualification() {
        assertProductionFrontendQualification(workflow);
        WorkflowDocument missing = openCi();
        WorkflowDocument.list(missing.job("verify").get("steps")).removeIf(step ->
                "Verify production frontend ownership and generated assets".equals(WorkflowDocument.map(step).get("name")));
        assertThatThrownBy(() -> assertProductionFrontendQualification(missing)).isInstanceOf(AssertionError.class);
    }

    private static void assertProductionFrontendQualification(WorkflowDocument ci) {
        assertThat(ci.steps("verify").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")))
                .containsSubsequence("Build and test", "Verify production frontend ownership and generated assets",
                        "Set up Temurin Java 21", "Verify Java 21 compatibility", "Upload browser qualification input");
        assertThat(ci.step("verify", "Build and test").get("run"))
                .isEqualTo("python3 scripts/ci/measure.py build-and-test app/build/ci/timings.jsonl ./gradlew --no-daemon --stacktrace clean applicationCheck :app:bootJar --profile");
        assertThat(ci.step("verify", "Verify production frontend ownership and generated assets").get("run"))
                .asString().contains("npm run check:boundaries", "npm run check:generated", "npm run format:check");
        assertBrowserQualification(ci);
    }

    private static int stepIndex(List<Object> steps, String name) {
        for (int index = 0; index < steps.size(); index++) {
            if (name.equals(WorkflowDocument.map(steps.get(index)).get("name"))) return index;
        }
        throw new IllegalArgumentException("Missing Verify step: " + name);
    }

    private static void assertBrowserQualification(WorkflowDocument ci) {
        assertThat(ci.job("browser").get("needs")).isEqualTo("verify");
        Map<String, Object> strategy = WorkflowDocument.map(ci.job("browser").get("strategy"));
        assertThat(strategy.get("fail-fast")).isEqualTo(false);
        var matrix = WorkflowDocument.list(WorkflowDocument.map(strategy.get("matrix")).get("include"));
        var expected = new java.util.ArrayList<Map<String, Object>>();
        for (var group : List.of(
                Map.of("name", "desktop", "suite", "chromium", "project", "desktop-1440"),
                Map.of("name", "tablet", "suite", "chromium", "project", "tablet-834"),
                Map.of("name", "mobile", "suite", "chromium", "project", "mobile-390"))) {
            for (int shard = 1; shard <= 2; shard++) {
                expected.add(Map.of("name", group.get("name") + "-" + shard + "of2", "suite", group.get("suite"),
                        "project", group.get("project"), "shard", shard + "/2"));
            }
        }
        expected.add(Map.of("name", "firefox-1of1", "suite", "cross", "project", "firefox-desktop", "shard", "1/1"));
        expected.add(Map.of("name", "webkit-1of1", "suite", "cross", "project", "webkit-desktop", "shard", "1/1"));
        assertThat(matrix.stream().map(WorkflowDocument::map).toList()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(strategy.get("max-parallel")).isEqualTo(8);
        assertThat(ci.steps("browser").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")))
                .containsSubsequence("Set up Temurin Java 21", "Set up Node 22", "Download verified BootJar",
                        "Stage verified browser artifact", "Install browser matrix",
                        "Qualify production homepage and browser matrix");
        var input = ci.step("verify", "Upload browser qualification input");
        assertThat(input).doesNotContainKey("if");
        assertThat(WorkflowDocument.map(input.get("with")))
                .containsEntry("name", "persefonia-browser-input-${{ github.sha }}")
                .containsEntry("if-no-files-found", "error");
        assertThat((String) ci.step("browser", "Download verified BootJar").get("uses"))
                .startsWith("actions/download-artifact@");
        assertThat(WorkflowDocument.map(ci.step("browser", "Download verified BootJar").get("with")))
                .containsEntry("name", "persefonia-browser-input-${{ github.sha }}")
                .containsEntry("path", "app/build/ci");
        String stage = (String) ci.step("browser", "Stage verified browser artifact").get("run");
        assertThat(stage).contains("sha256sum --check persefonia.jar.sha256",
                "cp app/build/ci/persefonia.jar app/build/libs/persefonia.jar",
                "BOOT-INF/classes/static/assets/.vite/manifest.json");
        assertThat(stage.indexOf("sha256sum --check")).isLessThan(stage.indexOf("cp app/build/ci"));
        assertThat(ci.step("browser", "Install browser matrix").get("run"))
                .asString().contains("npm ci --prefix frontend", "scripts/ci/measure.py").doesNotContain("playwright install");
        var qualification = ci.step("browser", "Qualify production homepage and browser matrix");
        assertThat(qualification.get("run")).asString()
                .contains("bash scripts/browser/check.sh --project \"$BROWSER_PROJECT\"");
        assertThat(WorkflowDocument.map(qualification.get("env")))
                .containsEntry("PERSEFONIA_BROWSER_SUITE", "${{ matrix.suite }}")
                .containsEntry("PERSEFONIA_BROWSER_SHARD", "${{ matrix.shard }}")
                .containsEntry("BROWSER_PROJECT", "${{ matrix.project }}");
    }

    @Test
    void browserReceiptsRequireIndependentCompleteDiscoveryAndExactArtifactIdentity() {
        var receipts = workflow.job("browser-receipts");
        assertThat(receipts.get("if")).isEqualTo("always()");
        assertThat(WorkflowDocument.list(receipts.get("needs"))).containsExactlyInAnyOrder("verify", "browser");
        assertThat(workflow.step("browser-receipts", "Reconcile independent discovery and execution").get("run"))
                .asString().contains("npm ci --prefix frontend", "browser-join.mjs");
        var download = WorkflowDocument.map(workflow.step("browser-receipts", "Download browser receipts").get("with"));
        assertThat(download).containsEntry("pattern", "persefonia-browser-receipt-*-${{ github.sha }}")
                .containsEntry("merge-multiple", true);
        assertThat(WorkflowDocument.map(workflow.step("browser", "Upload browser verification receipts").get("with")))
                .containsEntry("if-no-files-found", "error").containsEntry("retention-days", 14);
        assertThat(workflow.step("browser", "Upload browser verification receipts").get("if")).isEqualTo("always()");
        assertThat(workflow.step("verify", "Verify BootJar").get("run")).asString().contains("source-sha.txt", "$GITHUB_SHA");
        assertThat(workflow.step("browser", "Stage verified browser artifact").get("run")).asString().contains("source-sha.txt", "$GITHUB_SHA");
    }

    @Test
    void stableGateRequiresAllResultsAndHasNoRepositoryOrSecretAccess() {
        Map<String, Object> gate = workflow.job("gate");
        assertThat(WorkflowDocument.list(gate.get("needs"))).containsExactlyInAnyOrder("automation", "verify", "browser", "browser-receipts");
        assertThat(gate.get("if")).isEqualTo("always()");
        assertThat(WorkflowDocument.map(gate.get("permissions"))).isEmpty();
        assertThat(gate).doesNotContainKeys("services", "environment");
        assertThat(workflow.steps("gate")).hasSize(1);
        Map<String, Object> step = WorkflowDocument.map(workflow.steps("gate").getFirst());
        assertThat(step).doesNotContainKeys("uses", "secrets");
        assertThat(WorkflowDocument.map(step.get("env"))).containsKeys("AUTOMATION_RESULT", "VERIFY_RESULT", "BROWSER_RESULT", "RECEIPTS_RESULT");
        assertThat((String) step.get("run")).contains("$AUTOMATION_RESULT", "$VERIFY_RESULT", "$BROWSER_RESULT", "$RECEIPTS_RESULT", "success");
    }

    @Test
    void gateRejectsMissingFailedCancelledAndSkippedMandatoryWork() throws Exception {
        String script = (String) WorkflowDocument.map(workflow.steps("gate").getFirst()).get("run");
        Map<String, String> passing = Map.of("AUTOMATION_RESULT", "success", "VERIFY_RESULT", "success",
                "BROWSER_RESULT", "success", "RECEIPTS_RESULT", "success");
        assertThat(CommandRunner.command("bash").args("-e", "-c", script).env(passing).run().status()).isZero();
        for (String dependency : passing.keySet()) {
            for (String result : List.of("", "failure", "cancelled", "skipped")) {
                var values = new java.util.HashMap<>(passing);
                values.put(dependency, result);
                assertThat(CommandRunner.command("bash").args("-e", "-c", script).env(values).run().status())
                        .as(dependency + "=" + result).isNotZero();
            }
        }
    }
}

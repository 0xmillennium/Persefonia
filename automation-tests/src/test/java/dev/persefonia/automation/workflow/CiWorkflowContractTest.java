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
            WorkflowDocument.map(WorkflowDocument.map(changed.job("build-jvm").get("services")).get("redis"))
                    .put("image", mutable);
            assertThatThrownBy(() -> assertDigestPinnedServices(changed)).as(mutable)
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void browserQualificationUsesTheCiPostgresImage() throws IOException {
        String ciImage = (String) WorkflowDocument.map(
                WorkflowDocument.map(workflow.job("build-jvm").get("services")).get("postgres")).get("image");
        String script = Files.readString(Path.of("../scripts/browser/check.sh"));
        Matcher defaultImage = Pattern.compile("(?m)^postgres_image=\"\\$\\{POSTGRES_IMAGE:-([^}\\r\\n]+)}\"$")
                .matcher(script);
        assertThat(defaultImage.find()).isTrue();
        assertThat(defaultImage.group(1)).matches("[^\\s@]+@sha256:[0-9a-f]{64}").isEqualTo(ciImage);
        assertThat(script).contains("docker run", "\"$postgres_image\"");
        String server = Files.readString(Path.of("../app/src/integrationTest/java/dev/persefonia/app/testsupport/SharedPostgresTestServer.java"));
        assertThat(server).contains(ciImage, "asCompatibleSubstituteFor(\"postgres\")", "getImageId()");
    }

    @Test
    void localDependenciesUseTheAcceptedCiImages() throws IOException {
        Object localYaml = new Load(LoadSettings.builder().build())
                .loadFromString(Files.readString(Path.of("../compose.yaml")));
        Map<String, Object> localServices = WorkflowDocument.map(WorkflowDocument.map(localYaml).get("services"));
        Map<String, Object> ciServices = WorkflowDocument.map(workflow.job("build-jvm").get("services"));
        for (String service : List.of("postgres", "redis")) {
            assertThat(WorkflowDocument.map(localServices.get(service)).get("image"))
                    .as(service).isEqualTo(WorkflowDocument.map(ciServices.get(service)).get("image"));
        }
    }

    @Test
    void verifyChecksTheLocalComposeContract() {
        assertThat(workflow.step("build-jvm", "Validate local Compose contract").get("run"))
                .asString().contains("./scripts/ci/verify-local-compose.sh");
        try {
            Object localYaml = new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(Path.of("../compose.yaml")));
            String appImage = (String) WorkflowDocument.map(
                    WorkflowDocument.map(WorkflowDocument.map(localYaml).get("services")).get("app")).get("image");
            assertThat(workflow.step("build-jvm", "Verify development toolchain platforms").get("run"))
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
        Map<String, Object> services = WorkflowDocument.map(ci.job("build-jvm").get("services"));
        assertThat(services).containsOnlyKeys("postgres", "redis");
        for (var service : services.entrySet()) {
            assertThat((String) WorkflowDocument.map(service.getValue()).get("image"))
                    .as("verify/services/" + service.getKey() + "/image")
                    .matches("[^\\s@]+@sha256:[0-9a-f]{64}");
        }
    }

    @Test
    void automationFailsBeforeApplicationInfrastructureStarts() {
        assertThat(workflow.jobs()).containsOnlyKeys("automation", "eligibility", "compare", "build-jvm", "frontend", "verify", "browser", "gate");
        Map<String, Object> automation = workflow.job("automation");
        Map<String, Object> verify = workflow.job("build-jvm");
        assertThat(WorkflowDocument.map(automation.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(WorkflowDocument.map(verify.get("permissions")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        assertThat(automation).doesNotContainKeys("services");
        assertThat(automation.get("needs")).isNull();
        assertThat(WorkflowDocument.list(verify.get("needs"))).containsExactlyInAnyOrder("automation", "eligibility", "compare");
        assertThat(workflow.job("browser").get("needs")).isEqualTo("build-jvm");
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
        assertThat(workflow.step("build-jvm", "Build and test").get("run"))
                .asString().contains("applicationJvmCheck", ":app:bootJar");
    }

    @Test
    void browserSchedulingRequiresAnUncancelledSuccessfulProducerEvenAfterSkippedComparison() {
        // PR/off/ineligible (comparison skipped) and shadow/comparison misses run browsers
        // after Build + JVM succeeds. Failure, producer cancellation, workflow cancellation,
        // and a skipped producer on valid enforced reuse must not start the matrix.
        // This protects production YAML; hosted scheduling is a separate pilot observation.
        assertBrowserScheduling(workflow);
        WorkflowDocument missing = openCi();
        missing.job("browser").remove("if");
        assertThatThrownBy(() -> assertBrowserScheduling(missing)).isInstanceOf(AssertionError.class);
        for (String condition : List.of("needs.build-jvm.result == 'success'", "${{ !cancelled() }}", "always()")) {
            WorkflowDocument changed = openCi();
            changed.job("browser").put("if", condition);
            assertThatThrownBy(() -> assertBrowserScheduling(changed)).as(condition).isInstanceOf(AssertionError.class);
        }
        for (List<String> dependencies : List.of(List.of("build-jvm", "verify"), List.of("build-jvm", "frontend"))) {
            WorkflowDocument changed = openCi();
            changed.job("browser").put("needs", dependencies);
            assertThatThrownBy(() -> assertBrowserScheduling(changed)).as(dependencies.toString())
                    .isInstanceOf(AssertionError.class);
        }
    }

    private static void assertBrowserScheduling(WorkflowDocument ci) {
        assertThat(ci.job("browser").get("needs")).isEqualTo("build-jvm");
        Object condition = ci.job("browser").get("if");
        assertThat(condition).isInstanceOf(String.class);
        String expression = ((String) condition).strip();
        if (expression.startsWith("${{") && expression.endsWith("}}")) {
            expression = expression.substring(3, expression.length() - 2).strip();
        }
        assertThat(expression).matches("!cancelled\\(\\)\\s*&&\\s*needs\\.build-jvm\\.result\\s*==\\s*'success'");
    }

    @Test
    void browserMatrixRequiresTheVerifiedArtifactAndAllQualificationSteps() {
        assertBrowserQualification(workflow);
        for (String name : List.of("Download verified BootJar", "Stage verified browser artifact",
                "Install locked native dependencies", "Qualify shared native lifecycle")) {
            WorkflowDocument missing = openCi();
            WorkflowDocument.list(missing.job("browser").get("steps")).removeIf(step ->
                    name.equals(WorkflowDocument.map(step).get("name")));
            assertThatThrownBy(() -> assertBrowserQualification(missing)).isInstanceOf(AssertionError.class);
        }
        WorkflowDocument reordered = openCi();
        List<Object> steps = WorkflowDocument.list(reordered.job("browser").get("steps"));
        int browserIndex = stepIndex(steps, "Qualify shared native lifecycle");
        Collections.swap(steps, browserIndex, browserIndex - 1);
        assertThatThrownBy(() -> assertBrowserQualification(reordered)).isInstanceOf(AssertionError.class);
        WorkflowDocument bypassed = openCi();
        bypassed.step("browser", "Qualify shared native lifecycle")
                .put("run", "cd frontend && npm run test:browser");
        assertThatThrownBy(() -> assertBrowserQualification(bypassed)).isInstanceOf(AssertionError.class);
        WorkflowDocument narrowed = openCi();
        WorkflowDocument.map(narrowed.step("browser", "Qualify shared native lifecycle").get("env"))
                .put("PERSEFONIA_BROWSER_SUITE", "chromium");
        assertThatThrownBy(() -> assertBrowserQualification(narrowed)).isInstanceOf(AssertionError.class);
        WorkflowDocument matrix = openCi();
        matrix.job("browser").put("strategy", Map.of("matrix", Map.of("project", List.of("desktop-1440"))));
        assertThatThrownBy(() -> assertBrowserQualification(matrix)).isInstanceOf(AssertionError.class);
    }

    @Test
    void verifyRequiresProductionFrontendQualification() {
        assertProductionFrontendQualification(workflow);
        WorkflowDocument missing = openCi();
        WorkflowDocument.list(missing.job("build-jvm").get("steps")).removeIf(step ->
                "Verify production frontend ownership and generated assets".equals(WorkflowDocument.map(step).get("name")));
        assertThatThrownBy(() -> assertProductionFrontendQualification(missing)).isInstanceOf(AssertionError.class);
    }

    private static void assertProductionFrontendQualification(WorkflowDocument ci) {
        assertThat(ci.steps("build-jvm").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")))
                .containsSubsequence("Build and test", "Verify production frontend ownership and generated assets",
                        "Set up Temurin Java 21", "Verify Java 21 compatibility", "Upload browser qualification input");
        assertThat(ci.step("build-jvm", "Build and test").get("run"))
                .isEqualTo("python3 scripts/ci/measure.py build-and-test app/build/ci/timings.jsonl ./gradlew --no-daemon --stacktrace applicationJvmCheck :app:bootJar qualificationInputs --profile");
        assertThat(ci.step("build-jvm", "Verify production frontend ownership and generated assets").get("run"))
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
        assertBrowserScheduling(ci);
        assertThat(ci.job("browser")).doesNotContainKey("strategy");
        assertThat(ci.job("browser").get("name")).isEqualTo("Native browsers");
        assertThat(ci.steps("browser").stream().map(WorkflowDocument::map)
                .map(step -> (String) step.get("name")))
                .containsSubsequence("Set up Temurin Java 21", "Set up Node 22", "Download verified BootJar",
                        "Stage verified browser artifact", "Install locked native dependencies",
                        "Qualify shared native lifecycle");
        var input = ci.step("build-jvm", "Upload browser qualification input");
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
        assertThat(ci.step("browser", "Install locked native dependencies").get("run"))
                .asString().contains("npm ci --prefix frontend", "scripts/ci/measure.py").doesNotContain("playwright install");
        var qualification = ci.step("browser", "Qualify shared native lifecycle");
        assertThat(qualification.get("run")).asString()
                .contains("bash scripts/browser/check.sh").doesNotContain("--project", "--grep", "--shard");
        assertThat(WorkflowDocument.map(qualification.get("env")))
                .containsEntry("PERSEFONIA_BROWSER_SUITE", "native")
                .doesNotContainKeys("PERSEFONIA_BROWSER_SHARD", "BROWSER_PROJECT");
    }

    @Test
    void trustedGateOwnsIndependentNativeAdmissionWithoutAnotherInstall() {
        assertGateNativeAdmission(workflow);
        assertThat(workflow.jobs()).doesNotContainKey("browser-receipts");
        var changed = openCi();
        changed.step("gate", "Download browser receipts and raw reports").put("if", "false");
        final var filtered = changed;
        assertThatThrownBy(() -> assertGateNativeAdmission(filtered)).isInstanceOf(AssertionError.class);
        changed = openCi();
        WorkflowDocument.map(changed.step("gate", "Download browser receipts and raw reports").get("with"))
                .put("name", "persefonia-browser-acceptance-${{ github.sha }}");
        final var forged = changed;
        assertThatThrownBy(() -> assertGateNativeAdmission(forged)).isInstanceOf(AssertionError.class);
        assertFinalizedTimingHandoff(workflow);
    }

    private static void assertGateNativeAdmission(WorkflowDocument ci) {
        var download = ci.step("gate", "Download browser receipts and raw reports");
        assertThat(download.get("if")).isEqualTo("needs.compare.outputs.reuse != 'true'");
        assertThat(WorkflowDocument.map(download.get("with")))
                .containsEntry("name", "persefonia-browser-receipt-native-${{ github.sha }}")
                .containsEntry("path", "app/build/browser/verification");
        assertThat(ci.step("gate", "Require successful identity-bound qualification").get("run"))
                .isEqualTo("python3 scripts/ci/verification-summary.py gate");
        assertThat(ci.steps("gate").stream().map(WorkflowDocument::map))
                .noneMatch(step -> String.valueOf(step.get("run")).contains("npm ci"));
    }


    @Test
    void browserTimingRetentionCannotBeDroppedFromSuccessfulReceiptArtifacts() throws IOException {
        WorkflowDocument changed = WorkflowDocument.open("ci.yml");
        WorkflowDocument.map(changed.step("browser", "Upload browser verification receipts").get("with"))
                .put("path", "app/build/browser/verification/*-receipt.json");
        assertThatThrownBy(() -> assertFinalizedTimingHandoff(changed)).isInstanceOf(AssertionError.class);
    }

    private static void assertFinalizedTimingHandoff(WorkflowDocument ci) {
        var retention = ci.step("browser", "Retain finalized browser timings");
        assertThat(retention.get("if")).isEqualTo("always()");
        assertThat(WorkflowDocument.map(retention.get("env")))
                .containsEntry("BROWSER_PARTITION", "native");
        assertThat(retention.get("run")).asString().contains("app/build/browser/timings.jsonl",
                "${BROWSER_PARTITION}-setup-timings.jsonl", "${BROWSER_PARTITION}-npm-timings.jsonl");
        assertThat(((String) WorkflowDocument.map(ci.step("browser", "Upload browser verification receipts")
                .get("with")).get("path")).lines().toList())
                .containsExactly("app/build/browser/verification/*-receipt.json",
                        "app/build/browser/verification/native-discovery.json",
                        "app/build/browser/verification/native-results.json",
                        "app/build/browser/verification/*-timings.jsonl");
    }

    @Test
    void stableVerifyAndGateAlwaysReachStrictIdentityBoundAdmission() {
        assertThat(workflow.job("verify").get("name")).isEqualTo("Verify");
        assertThat(workflow.job("gate").get("name")).isEqualTo("Gate");
        for (String job : List.of("verify", "gate")) {
            assertThat(workflow.job(job).get("if")).isEqualTo("always()");
            assertThat(workflow.job(job)).doesNotContainKeys("services", "environment");
            assertThat(WorkflowDocument.map(workflow.job(job).get("permissions")))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("contents", "read"));
        }
        assertThat(WorkflowDocument.list(workflow.job("verify").get("needs")))
                .containsExactlyInAnyOrder("automation", "eligibility", "compare", "build-jvm", "frontend");
        assertThat(WorkflowDocument.list(workflow.job("gate").get("needs")))
                .containsExactlyInAnyOrder("automation", "eligibility", "compare", "build-jvm", "frontend", "verify", "browser");
        assertThat(workflow.step("verify", "Validate producer identities and non-browser admission").get("run"))
                .isEqualTo("python3 scripts/ci/verification-summary.py verify");
        assertThat(workflow.step("gate", "Require successful identity-bound qualification").get("run"))
                .isEqualTo("python3 scripts/ci/verification-summary.py gate");
        assertThat(WorkflowDocument.map(workflow.step("gate", "Require successful identity-bound qualification").get("env")))
                .containsEntry("QUALIFICATION_NEEDS", "${{ toJSON(needs) }}");
    }

    @Test
    void fullFrontendStressRunsIndependentlyAndReuseHasAnOperationalOffSwitch() throws IOException {
        assertThat(WorkflowDocument.list(workflow.job("frontend").get("needs")))
                .containsExactlyInAnyOrder("automation", "eligibility", "compare");
        assertThat(workflow.job("frontend").get("if")).isEqualTo(workflow.job("build-jvm").get("if"));
        assertThat(workflow.job("frontend").get("if")).asString()
                .contains("!cancelled()", "needs.automation.result == 'success'", "needs.eligibility.result == 'success'",
                        "needs.compare.result == 'skipped'", "needs.compare.result == 'success'", "needs.compare.outputs.reuse != 'true'");
        assertThat(workflow.step("frontend", "Execute fixed ordinary frontend tests").get("run"))
                .asString().contains("unit-run.mjs").doesNotContain("test:unit");
        assertThat(workflow.step("frontend", "Set up Temurin Java 25")).isNotEmpty();
        assertThat(workflow.step("frontend", "Prepare media tools").get("run"))
                .asString().contains("prepare-media-tools.py", "--mode install");
        assertThat(WorkflowDocument.map(workflow.step("eligibility", "Evaluate inexpensive trusted master eligibility").get("env")))
                .containsEntry("CI_QUALIFICATION_REUSE", "${{ vars.CI_QUALIFICATION_REUSE }}");
        assertThat(workflow.job("compare").get("if")).isEqualTo("needs.eligibility.outputs.eligible == 'true'");
        assertThat(workflow.step("compare", "Build preliminary production candidate").get("run"))
                .asString().contains(":app:bootJar qualificationInputs").doesNotContain("applicationJvmCheck");
        String root = Files.readString(Path.of("../build.gradle.kts"));
        String app = Files.readString(Path.of("../app/build.gradle.kts"));
        assertThat(root).contains("applicationJvmCheck", "applicationCheck", "frontendCheck", "automationCheck");
        assertThat(app).contains("dependsOn(jvmCheck)", "dependsOn(frontendCheck)", "dependsOn(npmRunUnitTest)",
                "dependsOn(tasks.test, integrationTest, migrationTest)");
    }

    @Test
    void actualWorkflowNamesMatchReuseAndDeliveryAdmission() throws Exception {
        var result = CommandRunner.command("python3").args("-B", "-c",
                "import sys; sys.path.insert(0,'scripts/ci'); import qualification as q; print('\\n'.join(q.CI_JOBS))")
                .directory(Path.of("..")).run();
        result.requireSuccess();
        assertThat(workflow.jobs().values().stream().map(WorkflowDocument::map)
                .map(job -> (String) job.get("name")))
                .containsExactlyInAnyOrderElementsOf(result.stdout().lines().toList());
    }

    @Test
    void ordinaryJobsStayBoundedAndNeverInjectAdminOrDeepProfiles() throws IOException {
        assertLeanContract(workflow);
        for (String mutation : List.of("native-budget", "admin", "deep")) {
            var changed = openCi();
            if (mutation.equals("native-budget")) changed.job("browser").put("timeout-minutes", 35);
            else WorkflowDocument.list(changed.job("build-jvm").get("steps")).add(Map.of("name", "unwanted", "run",
                    mutation.equals("admin") ? "python3 scripts/ci/admin-preview.py required" : "npm run test:unit:stress"));
            assertThatThrownBy(() -> assertLeanContract(changed)).as(mutation).isInstanceOf(AssertionError.class);
        }
        String lifecycle = Files.readString(Path.of("../frontend/scripts/verification/browser-lifecycle.mjs"));
        assertThat(lifecycle).contains("playwright('ordinary', [], discoveryPath, true)",
                "playwright('ordinary', [], resultsPath)").doesNotContain("browser-run.mjs");
    }

    private static void assertLeanContract(WorkflowDocument ci) {
        assertThat(((Number) ci.job("browser").get("timeout-minutes")).intValue()).isLessThanOrEqualTo(8);
        assertThat(((Number) ci.step("browser", "Qualify shared native lifecycle").get("timeout-minutes")).intValue())
                .isLessThanOrEqualTo(6);
        for (String job : List.of("build-jvm", "frontend", "browser")) {
            assertThat(ci.steps(job).stream().map(WorkflowDocument::map)
                    .map(step -> String.valueOf(step.get("run"))))
                    .noneMatch(run -> run.contains("admin-preview") || run.contains("test:unit:stress") || run.contains("--comprehensive"));
        }
    }
}

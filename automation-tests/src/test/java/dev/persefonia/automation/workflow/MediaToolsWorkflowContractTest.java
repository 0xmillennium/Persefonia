package dev.persefonia.automation.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.CommandRunner;
import dev.persefonia.automation.support.WorkflowDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaToolsWorkflowContractTest {
    @TempDir Path temporary;
    private static final Map<String, String> UPLOADS = Map.of(
            "frontend", "Retain frontend diagnostics", "build-jvm", "Upload build diagnostics",
            "compare", "Retain comparison diagnostics", "browser", "Upload browser diagnostics");
    private static final String PREPARE = "python3 scripts/ci/prepare-media-tools.py --mode install --report-dir \"$RUNNER_TEMP/persefonia-media-tools\"";

    @Test
    void everyIndependentConsumerPreparesToolsBeforeRuntimeSetupAndRetainsEarlyDiagnostics() throws Exception {
        WorkflowDocument ci = WorkflowDocument.open("ci.yml");
        for (String job : UPLOADS.keySet()) assertMediaPreparation(ci, job);
        for (String job : List.of("automation", "eligibility", "verify", "browser-receipts", "gate")) {
            assertThat(ci.steps(job).stream().map(WorkflowDocument::map))
                    .noneMatch(step -> "media_tools".equals(step.get("id")));
        }
    }

    @Test
    void removingReorderingConditioningOrSwallowingMediaSetupFailsTheActualYamlContract() throws Exception {
        for (String job : UPLOADS.keySet()) {
            for (String mutation : List.of("removed", "late", "firefox-only", "swallowed", "ignored", "missing-diagnostics", "late-finalizer")) {
                WorkflowDocument changed = WorkflowDocument.open("ci.yml");
                var setup = changed.step(job, "Prepare media tools");
                var steps = changed.steps(job);
                switch (mutation) {
                    case "removed" -> steps.remove(setup);
                    case "late" -> Collections.swap(steps, 1, 2);
                    case "firefox-only" -> setup.put("if", "matrix.project == 'firefox-desktop'");
                    case "swallowed" -> setup.put("run", PREPARE + " || true");
                    case "ignored" -> setup.put("continue-on-error", true);
                    case "missing-diagnostics" -> WorkflowDocument.map(changed.step(job, UPLOADS.get(job)).get("with"))
                            .put("path", "frontend/test-results/**");
                    case "late-finalizer" -> {
                        var finalizer = changed.step(job, "Finalize media prerequisite diagnostics");
                        steps.remove(finalizer);
                        steps.add(finalizer);
                    }
                    default -> throw new IllegalArgumentException(mutation);
                }
                assertThatThrownBy(() -> assertMediaPreparation(changed, job)).as(job + ": " + mutation)
                        .isInstanceOf(AssertionError.class);
            }
        }
    }

    private static void assertMediaPreparation(WorkflowDocument ci, String job) {
        var steps = ci.steps(job).stream().map(WorkflowDocument::map).toList();
        assertThat(steps.get(0).get("name")).isEqualTo("Check out repository");
        var setup = steps.get(1);
        assertThat(setup).containsEntry("name", "Prepare media tools").containsEntry("id", "media_tools")
                .containsEntry("run", PREPARE).doesNotContainKeys("if", "continue-on-error");
        assertThat(steps.stream().filter(step -> "media_tools".equals(step.get("id"))).count()).isEqualTo(1);
        var finalizer = steps.stream().filter(step -> "Finalize media prerequisite diagnostics".equals(step.get("name")))
                .findFirst().orElse(null);
        assertThat(finalizer).isNotNull();
        assertThat(finalizer).containsEntry("if", "always()").doesNotContainKey("continue-on-error");
        assertThat(WorkflowDocument.map(finalizer.get("env")))
                .containsEntry("MEDIA_STEP_OUTCOME", "${{ steps.media_tools.outcome }}")
                .containsEntry("MEDIA_JOB_STATUS", "${{ job.status }}")
                .containsEntry("MEDIA_JOB", job)
                .containsEntry("MEDIA_REPORT_ROOT", "${{ runner.temp }}/persefonia-media-tools");
        var upload = ci.step(job, UPLOADS.get(job));
        assertThat(steps.indexOf(finalizer)).isLessThan(steps.indexOf(upload));
        assertThat(WorkflowDocument.map(upload.get("with")).get("path")).asString()
                .contains("app/build/ci/media-prerequisites/**");
        if (!job.equals("browser")) {
            assertThat(WorkflowDocument.map(upload.get("with"))).containsEntry("if-no-files-found", "error");
        }
    }

    @Test
    void failedSetupStopsTestsAndSurvivesBuildCleanWhileTheActualFinalizerNeedsNoCheckout() throws Exception {
        WorkflowDocument ci = WorkflowDocument.open("ci.yml");
        Path fixture = Files.createDirectory(temporary.resolve("fixture"));
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Files.createSymbolicLink(bin.resolve("python3"), Path.of("/usr/bin/python3"));
        Path helper = fixture.resolve("scripts/ci/prepare-media-tools.py");
        Files.createDirectories(helper.getParent());
        Files.copy(Path.of("../scripts/ci/prepare-media-tools.py"), helper);
        // Shadow every mutation-capable command. No host package operation is reachable.
        for (String command : List.of("apt-get", "sudo")) {
            Path stub = bin.resolve(command);
            Files.writeString(stub, "#!/bin/sh\nexit 19\n");
            assertThat(stub.toFile().setExecutable(true)).isTrue();
        }
        Path runnerTemp = temporary.resolve("runner");
        Files.createDirectories(runnerTemp);
        String preparation = (String) ci.step("frontend", "Prepare media tools").get("run");
        String sentinel = "\npython3 -c \"from pathlib import Path; Path('tests-ran').touch()\"";
        var failed = CommandRunner.command("/bin/bash").args("-e", "-c", preparation + sentinel)
                .directory(fixture).path(bin.toString()).env("RUNNER_TEMP", runnerTemp.toString()).run();
        assertThat(failed.status()).isNotZero();
        assertThat(Files.exists(fixture.resolve("tests-ran"))).isFalse();
        Path source = runnerTemp.resolve("persefonia-media-tools");
        try (var invocations = Files.list(source)) {
            assertThat(invocations.anyMatch(path -> Files.exists(path.resolve("setup.json")))).isTrue();
        }
        // An actual clean of this disposable build path cannot touch runner-temp evidence.
        Path build = Files.createDirectories(fixture.resolve("app/build"));
        Files.writeString(build.resolve("before-clean"), "disposable");
        CommandRunner.command("/bin/rm").args("-rf", build.toString()).directory(fixture).run().requireSuccess();
        Files.move(fixture.resolve("scripts"), fixture.resolve("checkout-unavailable"));
        for (String job : UPLOADS.keySet()) {
            String finalizer = (String) ci.step(job, "Finalize media prerequisite diagnostics").get("run");
            runFinalizer(fixture, bin, source, finalizer, job, "failure", "failure");
            String diagnostic = Files.readString(fixture.resolve("app/build/ci/media-prerequisites/job-status.json"));
            assertThat(diagnostic).contains("\"stepOutcome\": \"failure\"", "\"jobStatus\": \"failure\"", "\"setupRecords\": \"available\"");
            try (var invocations = Files.list(source)) {
                for (Path invocation : invocations.toList()) {
                    assertThat(Files.readString(fixture.resolve("app/build/ci/media-prerequisites")
                            .resolve(invocation.getFileName()).resolve("setup.json")))
                            .isEqualTo(Files.readString(invocation.resolve("setup.json")))
                            .contains("\"status\": \"failed\"");
                }
            }
            assertThat(Files.exists(fixture.resolve("tests-ran"))).isFalse();
            assertThat(Files.exists(fixture.resolve("app/build/ci/frontend.json"))).isFalse();
        }
        Path absent = fixture.resolve("not-started");
        Files.createDirectory(absent);
        runFinalizer(absent, bin, temporary.resolve("no-reports"),
                (String) ci.step("frontend", "Finalize media prerequisite diagnostics").get("run"), "frontend", "", "failure");
        assertThat(Files.readString(absent.resolve("app/build/ci/media-prerequisites/job-status.json")))
                .contains("\"stepOutcome\": \"not-started\"", "\"setupRecords\": \"unavailable\"");
    }

    private static void runFinalizer(Path directory, Path bin, Path source, String script,
            String job, String outcome, String status) throws Exception {
        CommandRunner.command("/bin/bash").args("-e", "-c", script).directory(directory).path(bin.toString())
                .env(Map.of("MEDIA_REPORT_ROOT", source.toString(), "MEDIA_STEP_OUTCOME", outcome,
                        "MEDIA_JOB_STATUS", status, "MEDIA_JOB", job)).run().requireSuccess();
    }
}

package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserProvisionContractTest {
    @TempDir Path temporary;

    @Test
    void oneOwnerChecksMediaBeforeEverySelectedEngineAndKeepsInstallationOptIn() throws Exception {
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Path log = temporary.resolve("arguments");
        Path packagesLog = Path.of(log + ".packages");
        Map<String, String> commands = Map.of(
                "node", "printf '%s\\n' \"$@\" > \"$PROVISION_LOG\"\n"
                        + "printf 'browser-download\\n' >> \"$PROVISION_LOG.order\"\n",
                "python3", "[[ $1 == */scripts/ci/prepare-media-tools.py ]] || exit 21\n"
                        + "printf '%s\\n' \"$@\" > \"$PROVISION_LOG.media\"\n"
                        + "printf 'media-check\\n' >> \"$PROVISION_LOG.order\"\n",
                "apt-get", "printf '%s\\n' \"$*\" >> \"$PROVISION_LOG.packages\"\n",
                "sudo", "[[ $1 == -n ]] || exit 22\nshift\nexec \"$@\"\n");
        for (var command : commands.entrySet()) {
            Path executable = bin.resolve(command.getKey());
            Files.writeString(executable, "#!/usr/bin/env bash\nset -euo pipefail\n" + command.getValue());
            assertThat(executable.toFile().setExecutable(true)).isTrue();
        }
        Path script = Path.of("../scripts/browser/provision.sh").toAbsolutePath();
        record Selection(String suite, String project, List<String> engines) {}
        for (String optIn : List.of("local", "CI", "PERSEFONIA_BROWSER_WITH_DEPS")) {
            for (var selection : List.of(
                    new Selection("chromium", "*", List.of("chromium")),
                    new Selection("cross", "firefox-desktop", List.of("firefox")),
                    new Selection("cross", "webkit-desktop", List.of("webkit")),
                    new Selection("all", "*", List.of("chromium", "firefox", "webkit")))) {
                Files.deleteIfExists(packagesLog);
                Files.deleteIfExists(Path.of(log + ".order"));
                var command = CommandRunner.command("bash").args(script.toString(), selection.suite(), selection.project())
                        .pathPrepend(bin).env("PROVISION_LOG", log.toString())
                        .env("PERSEFONIA_MEDIA_REPORT_DIR", temporary.resolve("media-reports").toString());
                if (!optIn.equals("local")) command.env(optIn, "1");
                command.run().requireSuccess();
                var arguments = Files.readAllLines(log);
                var prefix = new java.util.ArrayList<>(List.of("node_modules/playwright/cli.js", "install", "--no-shell"));
                if (!optIn.equals("local")) prefix.add("--with-deps");
                prefix.addAll(selection.engines());
                assertThat(arguments).containsExactlyElementsOf(prefix);
                boolean install = !optIn.equals("local") && System.getProperty("os.name").equals("Linux");
                assertThat(Files.readAllLines(Path.of(log + ".media"))).containsExactly(
                        script.getParent().getParent().resolve("ci/prepare-media-tools.py").normalize().toString(),
                        "--mode", install ? "install" : "check", "--report-dir", temporary.resolve("media-reports").toString());
                assertThat(Files.readAllLines(Path.of(log + ".order"))).containsExactly("media-check", "browser-download");
                boolean firefoxPackages = install && selection.engines().contains("firefox");
                assertThat(Files.exists(packagesLog)).isEqualTo(firefoxPackages);
                if (firefoxPackages) assertThat(Files.readAllLines(packagesLog))
                        .containsExactly("update", "install -y --no-install-recommends xauth");
            }
        }
        for (List<String> bad : List.of(List.of("cross", "unknown"), List.of("unknown"))) {
            assertThat(CommandRunner.command("bash").args(script.toString()).args(bad).pathPrepend(bin)
                    .env("PROVISION_LOG", log.toString()).run().status()).isNotZero();
        }
        String wrapper = Files.readString(Path.of("../scripts/browser/check.sh"));
        assertThat(wrapper).contains("scripts/browser/provision.sh").doesNotContain("npx playwright install");
        assertThat(wrapper.indexOf("scripts/browser/provision.sh")).isLessThan(wrapper.indexOf("docker run"));
    }

    @Test
    void nativeFirefoxGetsAVirtualDisplayWithoutChangingMandatoryRunnerArguments() throws Exception {
        Path bin = Files.createDirectory(temporary.resolve("runner-bin"));
        Path log = temporary.resolve("runner-arguments");
        Map<String, String> commands = Map.of(
                "node", "if [[ $1 == node_modules/playwright/cli.js ]]; then exit 0; fi\n"
                        + "printf '%s\\n' \"${DISPLAY:-}\" \"$@\" > \"$BROWSER_LOG\"\n",
                "python3", "if [[ $1 == */measure.py ]]; then shift 3; exec \"$@\"; fi\n",
                "docker", "case $1 in run) echo test-container ;; port) echo 127.0.0.1:15432 ;; esac\n",
                "java", "exec sleep 60\n",
                "curl", "exit 0\n",
                "apt-get", "exit 0\n",
                "sudo", "[[ $1 == -n ]] || exit 22\nshift\nexec \"$@\"\n",
                "xvfb-run", "[[ $1 == --auto-servernum ]] || exit 2\nshift\nexport DISPLAY=:99\nexec \"$@\"\n");
        for (var command : commands.entrySet()) {
            Path executable = bin.resolve(command.getKey());
            Files.writeString(executable, "#!/usr/bin/env bash\nset -euo pipefail\n" + command.getValue());
            assertThat(executable.toFile().setExecutable(true)).isTrue();
        }
        record Lane(String suite, String project, String display, String expectedDisplay) {}
        for (var lane : List.of(
                new Lane("cross", "firefox-desktop", "", ":99"),
                new Lane("cross", "firefox-desktop", ":42", ":42"),
                new Lane("cross", "webkit-desktop", "", ""),
                new Lane("chromium", "mobile-390", "", ""))) {
            CommandRunner.command("bash").args(Path.of("../scripts/browser/check.sh").toAbsolutePath().toString(),
                            "--project", lane.project()).pathPrepend(bin)
                    .env("BROWSER_LOG", log.toString()).env("CI", "true").env("DISPLAY", lane.display())
                    .env("PERSEFONIA_BROWSER_SUITE", lane.suite())
                    .env("PERSEFONIA_BROWSER_ARTIFACT_DIR", temporary.resolve("artifacts").toString())
                    .run().requireSuccess();
            String display = System.getProperty("os.name").equals("Linux")
                    ? lane.expectedDisplay() : lane.display();
            assertThat(Files.readAllLines(log)).containsExactly(display,
                    "scripts/verification/browser-run.mjs", lane.suite(), "--project", lane.project());
        }
    }
}

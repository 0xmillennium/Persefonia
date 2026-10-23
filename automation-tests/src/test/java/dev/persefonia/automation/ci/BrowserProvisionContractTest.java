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
                    new Selection("admin", "*", List.of("chromium")),
                    new Selection("diagnostics", "*", List.of("chromium", "firefox", "webkit")),
                    new Selection("native", "*", List.of("chromium", "firefox", "webkit")))) {
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
        for (String display : List.of("", ":42")) {
            CommandRunner.command("bash").args(Path.of("../scripts/browser/check.sh").toAbsolutePath().toString())
                    .pathPrepend(bin).env("BROWSER_LOG", log.toString()).env("CI", "true").env("DISPLAY", display)
                    .env("PERSEFONIA_BROWSER_SUITE", "native")
                    .env("PERSEFONIA_BROWSER_ARTIFACT_DIR", temporary.resolve("artifacts").toString())
                    .run().requireSuccess();
            String expected = System.getProperty("os.name").equals("Linux") && display.isEmpty() ? ":99" : display;
            assertThat(Files.readAllLines(log)).containsExactly(expected, "scripts/verification/browser-lifecycle.mjs");
        }
        for (String suite : List.of("cross", "chromium")) {
            assertThat(CommandRunner.command("bash").args(Path.of("../scripts/browser/check.sh").toAbsolutePath().toString())
                    .pathPrepend(bin).env("BROWSER_LOG", log.toString()).env("CI", "true")
                    .env("PERSEFONIA_BROWSER_SUITE", suite).run().status()).isEqualTo(2);
        }
    }

    @Test
    void occupiedApplicationPortCannotQualifyAnUnrelatedExistingServer() throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            var result = CommandRunner.command("bash")
                    .args(Path.of("../scripts/browser/check.sh").toAbsolutePath().toString())
                    .env("PERSEFONIA_BROWSER_SUITE", "native")
                    .env("PERSEFONIA_BROWSER_PORT", String.valueOf(listener.getLocalPort()))
                    .run();
            assertThat(result.status()).isNotZero();
            assertThat(result.stderr()).contains("refusing to test an existing server");
            assertThat(result.stdout()).doesNotContain("Browser PostgreSQL image");
        }
    }

}

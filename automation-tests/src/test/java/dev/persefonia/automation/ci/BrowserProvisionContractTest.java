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
    void oneOwnerInstallsOnlyTheSelectedLockedEnginesAndCiSystemDependencies() throws Exception {
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Path log = temporary.resolve("arguments");
        Path packagesLog = Path.of(log + ".packages");
        Path node = bin.resolve("node");
        Files.writeString(node, "#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > \"$PROVISION_LOG\"\n");
        assertThat(node.toFile().setExecutable(true)).isTrue();
        Path apt = bin.resolve("apt-get");
        Files.writeString(apt, "#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > \"$PROVISION_LOG.packages\"\n");
        assertThat(apt.toFile().setExecutable(true)).isTrue();
        Path sudo = bin.resolve("sudo");
        Files.writeString(sudo, "#!/usr/bin/env bash\nexec \"$@\"\n");
        assertThat(sudo.toFile().setExecutable(true)).isTrue();
        Path script = Path.of("../scripts/browser/provision.sh").toAbsolutePath();
        for (String suite : List.of("chromium", "cross", "all")) {
            Files.deleteIfExists(packagesLog);
            var command = CommandRunner.command("bash").args(script.toString(), suite).pathPrepend(bin)
                    .env("PROVISION_LOG", log.toString()).env("CI", "true");
            command.run().requireSuccess();
            var arguments = Files.readAllLines(log);
            assertThat(arguments).startsWith("node_modules/playwright/cli.js", "install", "--no-shell", "--with-deps");
            assertThat(arguments.subList(4, arguments.size())).containsExactlyElementsOf(switch (suite) {
                case "chromium" -> List.of("chromium");
                case "cross" -> List.of("firefox", "webkit");
                default -> List.of("chromium", "firefox", "webkit");
            });
            assertFirefoxPackages(packagesLog, !suite.equals("chromium"));
        }
        for (String engine : List.of("firefox", "webkit")) {
            Files.deleteIfExists(packagesLog);
            CommandRunner.command("bash").args(script.toString(), "cross", engine + "-desktop").pathPrepend(bin)
                    .env("PROVISION_LOG", log.toString()).env("CI", "true").run().requireSuccess();
            assertThat(Files.readAllLines(log)).containsExactly(
                    "node_modules/playwright/cli.js", "install", "--no-shell", "--with-deps", engine);
            assertFirefoxPackages(packagesLog, engine.equals("firefox"));
        }
        assertThat(CommandRunner.command("bash").args(script.toString(), "cross", "unknown").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().status()).isNotZero();
        CommandRunner.command("bash").args(script.toString(), "chromium").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().requireSuccess();
        assertThat(Files.readAllLines(log)).doesNotContain("--with-deps");
        Files.deleteIfExists(packagesLog);
        CommandRunner.command("bash").args(script.toString(), "cross", "firefox-desktop").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().requireSuccess();
        assertFirefoxPackages(packagesLog, false);
        CommandRunner.command("bash").args(script.toString(), "cross", "firefox-desktop").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).env("PERSEFONIA_BROWSER_WITH_DEPS", "1")
                .run().requireSuccess();
        assertThat(Files.readAllLines(log)).contains("--with-deps");
        assertFirefoxPackages(packagesLog, true);
        assertThat(CommandRunner.command("bash").args(script.toString(), "unknown").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().status()).isNotZero();
        String wrapper = Files.readString(Path.of("../scripts/browser/check.sh"));
        assertThat(wrapper).contains("scripts/browser/provision.sh").doesNotContain("npx playwright install");
        assertThat(wrapper.indexOf("scripts/browser/provision.sh")).isLessThan(wrapper.indexOf("docker run"));
    }

    private void assertFirefoxPackages(Path log, boolean firefox) throws Exception {
        boolean expected = firefox && System.getProperty("os.name").equals("Linux");
        assertThat(Files.exists(log)).isEqualTo(expected);
        if (expected) assertThat(Files.readAllLines(log)).containsExactly(
                "install", "-y", "--no-install-recommends", "ffmpeg", "xauth");
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
                "sudo", "exec \"$@\"\n",
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

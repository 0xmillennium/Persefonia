package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserProvisionContractTest {
    @TempDir Path temporary;

    @Test
    void oneOwnerInstallsOnlyTheSelectedLockedEnginesAndCiSystemDependencies() throws Exception {
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Path log = temporary.resolve("arguments");
        Path node = bin.resolve("node");
        Files.writeString(node, "#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > \"$PROVISION_LOG\"\n");
        assertThat(node.toFile().setExecutable(true)).isTrue();
        Path script = Path.of("../scripts/browser/provision.sh").toAbsolutePath();
        for (String suite : List.of("chromium", "cross", "all")) {
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
        }
        for (String engine : List.of("firefox", "webkit")) {
            CommandRunner.command("bash").args(script.toString(), "cross", engine + "-desktop").pathPrepend(bin)
                    .env("PROVISION_LOG", log.toString()).env("CI", "true").run().requireSuccess();
            assertThat(Files.readAllLines(log)).containsExactly(
                    "node_modules/playwright/cli.js", "install", "--no-shell", "--with-deps", engine);
        }
        assertThat(CommandRunner.command("bash").args(script.toString(), "cross", "unknown").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().status()).isNotZero();
        CommandRunner.command("bash").args(script.toString(), "chromium").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().requireSuccess();
        assertThat(Files.readAllLines(log)).doesNotContain("--with-deps");
        assertThat(CommandRunner.command("bash").args(script.toString(), "unknown").pathPrepend(bin)
                .env("PROVISION_LOG", log.toString()).run().status()).isNotZero();
        String wrapper = Files.readString(Path.of("../scripts/browser/check.sh"));
        assertThat(wrapper).contains("scripts/browser/provision.sh").doesNotContain("npx playwright install");
        assertThat(wrapper.indexOf("scripts/browser/provision.sh")).isLessThan(wrapper.indexOf("docker run"));
    }
}

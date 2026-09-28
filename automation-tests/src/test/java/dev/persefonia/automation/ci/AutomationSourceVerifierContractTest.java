package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomationSourceVerifierContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void verifiesAllTrackedSourcesWithoutNetworkAndRejectsSyntaxErrors() throws Exception {
        Fixture fixture = fixture();
        fixture.run().requireSuccess();
        assertThat(Files.readString(fixture.log)).contains(".github/workflows/ci.yml", "scripts/ci/valid.sh",
                "automation-tests/src/test/resources/fake.sh");
        Path invalid = fixture.root.resolve("scripts/invalid.sh");
        Files.writeString(invalid, "#!/usr/bin/env bash\nif then\n");
        fixture.git("add", "scripts/invalid.sh").requireSuccess();
        CommandResult result = fixture.run();
        assertThat(result.status()).isNotZero();
        assertThat(result.stderr()).contains("syntax error");
    }

    @Test
    void rejectsUnexpectedToolVersionBeforeAnalysis() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.tools.resolve("actionlint"), "#!/usr/bin/env bash\nprintf '9.9.9\\nsynthetic\\n'\n");
        assertThat(fixture.run().status()).isNotZero();
        assertThat(Files.exists(fixture.log)).isFalse();
    }

    private Fixture fixture() throws Exception {
        Path root = temporary.resolve("repository");
        Path scripts = root.resolve("scripts/ci");
        Path workflows = root.resolve(".github/workflows");
        Path resources = root.resolve("automation-tests/src/test/resources");
        Path tools = temporary.resolve("tools");
        Files.createDirectories(scripts);
        Files.createDirectories(workflows);
        Files.createDirectories(resources);
        Files.createDirectories(tools);
        Files.copy(repository.resolve("scripts/ci/verify-automation-source.sh"), scripts.resolve("verify-automation-source.sh"));
        scripts.resolve("verify-automation-source.sh").toFile().setExecutable(true);
        Files.copy(repository.resolve("scripts/ci/automation-toolchain.json"), scripts.resolve("automation-toolchain.json"));
        Files.writeString(workflows.resolve("ci.yml"), "name: Test\non: push\njobs: {}\n");
        Path valid = scripts.resolve("valid.sh");
        Files.writeString(valid, "#!/usr/bin/env bash\nset -euo pipefail\ntrue\n");
        Files.writeString(resources.resolve("fake.sh"), "#!/usr/bin/env bash\nset -euo pipefail\ntrue\n");
        Path log = temporary.resolve("analysis.log");
        Path actionlint = tools.resolve("actionlint");
        Files.writeString(actionlint, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ ${1:-} == -version ]]; then printf '1.7.12\\nsynthetic\\n'; exit; fi
                printf 'actionlint %s\\n' "$*" >> "$ANALYSIS_LOG"
                """);
        actionlint.toFile().setExecutable(true);
        Path shellcheck = tools.resolve("shellcheck");
        Files.writeString(shellcheck, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ ${1:-} == --version ]]; then printf 'version: 0.11.0\\n'; exit; fi
                printf 'shellcheck %s\\n' "$*" >> "$ANALYSIS_LOG"
                """);
        shellcheck.toFile().setExecutable(true);
        Fixture fixture = new Fixture(root, tools, log);
        fixture.git("init", "-q").requireSuccess();
        fixture.git("add", ".").requireSuccess();
        return fixture;
    }

    private record Fixture(Path root, Path tools, Path log) {
        CommandResult git(String... arguments) throws Exception {
            String[] command = new String[arguments.length + 1];
            command[0] = "git";
            System.arraycopy(arguments, 0, command, 1, arguments.length);
            return new CommandRunner(root, root.resolve("home"), Duration.ofSeconds(10), Map.of()).run(command);
        }

        CommandResult run() throws Exception {
            return new CommandRunner(root, root.resolve("home"), Duration.ofSeconds(10),
                    Map.of("ANALYSIS_LOG", log.toString()))
                    .run(root.resolve("scripts/ci/verify-automation-source.sh").toString(), tools.toString());
        }
    }
}

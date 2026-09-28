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

class AutomationToolInstallerContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void installsOnlyChecksumVerifiedVersionedToolsUnderRequestedDirectory() throws Exception {
        Fixture fixture = fixture();
        CommandResult result = fixture.run();
        result.requireSuccess();
        assertThat(Files.isExecutable(fixture.target.resolve("actionlint"))).isTrue();
        assertThat(Files.isExecutable(fixture.target.resolve("shellcheck"))).isTrue();
        assertThat(Files.list(fixture.target).map(path -> path.getFileName().toString()).toList())
                .containsExactlyInAnyOrder("actionlint", "shellcheck");
    }

    @Test
    void rejectsChecksumMismatchAndMalformedLockWithoutInstallingTool() throws Exception {
        Fixture fixture = fixture();
        Path lock = fixture.root.resolve("scripts/ci/automation-toolchain.json");
        String original = Files.readString(lock);
        Files.writeString(lock, original.replace(fixture.actionDigest, "0".repeat(64)));
        assertThat(fixture.run().status()).isNotZero();
        assertThat(Files.exists(fixture.target.resolve("actionlint"))).isFalse();
        Files.writeString(lock, "{\"actionlint\":{}}\n");
        assertThat(fixture.run().status()).isNotZero();
        assertThat(Files.exists(fixture.target.resolve("shellcheck"))).isFalse();
    }

    @Test
    void rejectsMissingReleaseArtifactAndWrongExtractedVersion() throws Exception {
        Fixture fixture = fixture();
        Path archive = fixture.artifacts.resolve("actionlint_1.7.12_linux_amd64.tar.gz");
        Files.delete(archive);
        assertThat(fixture.run().status()).isNotZero();
        assertThat(Files.exists(fixture.target.resolve("actionlint"))).isFalse();

        Path executable = temporary.resolve("actionlint");
        Files.writeString(executable, "#!/usr/bin/env bash\nprintf '9.9.9\\nsynthetic\\n'\n");
        executable.toFile().setExecutable(true);
        CommandRunner archiver = new CommandRunner(temporary, temporary.resolve("home"), Duration.ofSeconds(10), Map.of());
        archiver.run("tar", "-czf", archive.toString(), "actionlint").requireSuccess();
        String wrongDigest = archiver.run("sha256sum", archive.toString()).stdout().split(" ")[0];
        Path lock = fixture.root.resolve("scripts/ci/automation-toolchain.json");
        Files.writeString(lock, Files.readString(lock).replace(fixture.actionDigest, wrongDigest));
        assertThat(fixture.run().status()).isNotZero();
        assertThat(Files.exists(fixture.target.resolve("actionlint"))).isFalse();
    }

    private Fixture fixture() throws Exception {
        Path root = temporary.resolve("repository");
        Path scripts = root.resolve("scripts/ci");
        Path artifacts = temporary.resolve("artifacts");
        Path bin = temporary.resolve("bin");
        Path target = temporary.resolve("installed");
        Files.createDirectories(scripts);
        Files.createDirectories(artifacts);
        Files.createDirectories(bin);
        Path installer = scripts.resolve("install-automation-tools.sh");
        Files.copy(repository.resolve("scripts/ci/install-automation-tools.sh"), installer);
        Path action = temporary.resolve("actionlint");
        Files.writeString(action, "#!/usr/bin/env bash\nif [[ ${1:-} == -version ]]; then printf '1.7.12\\nsynthetic\\n'; fi\n");
        action.toFile().setExecutable(true);
        Path shellDir = temporary.resolve("shellcheck-v0.11.0");
        Files.createDirectories(shellDir);
        Path shell = shellDir.resolve("shellcheck");
        Files.writeString(shell, "#!/usr/bin/env bash\nif [[ ${1:-} == --version ]]; then printf 'version: 0.11.0\\n'; fi\n");
        shell.toFile().setExecutable(true);
        CommandRunner archiver = new CommandRunner(temporary, temporary.resolve("home"), Duration.ofSeconds(10), Map.of());
        archiver.run("tar", "-czf", artifacts.resolve("actionlint_1.7.12_linux_amd64.tar.gz").toString(), "actionlint").requireSuccess();
        archiver.run("tar", "-cJf", artifacts.resolve("shellcheck-v0.11.0.linux.x86_64.tar.xz").toString(), "shellcheck-v0.11.0").requireSuccess();
        String actionDigest = archiver.run("sha256sum", artifacts.resolve("actionlint_1.7.12_linux_amd64.tar.gz").toString()).stdout().split(" ")[0];
        String shellDigest = archiver.run("sha256sum", artifacts.resolve("shellcheck-v0.11.0.linux.x86_64.tar.xz").toString()).stdout().split(" ")[0];
        Files.writeString(scripts.resolve("automation-toolchain.json"), """
                {
                  "actionlint":{"version":"1.7.12","artifact":"actionlint_1.7.12_linux_amd64.tar.gz","url":"https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz","sha256":"%s"},
                  "shellcheck":{"version":"0.11.0","artifact":"shellcheck-v0.11.0.linux.x86_64.tar.xz","url":"https://github.com/koalaman/shellcheck/releases/download/v0.11.0/shellcheck-v0.11.0.linux.x86_64.tar.xz","sha256":"%s"}
                }
                """.formatted(actionDigest, shellDigest));
        Path curl = bin.resolve("curl");
        Files.writeString(curl, """
                #!/usr/bin/env bash
                set -euo pipefail
                output=
                url=
                while (($#)); do
                  if [[ $1 == --output ]]; then output=$2; shift 2; else url=$1; shift; fi
                done
                cp -- "$FAKE_ARTIFACTS/${url##*/}" "$output"
                """);
        curl.toFile().setExecutable(true);
        return new Fixture(root, bin, artifacts, target, actionDigest);
    }

    private record Fixture(Path root, Path bin, Path artifacts, Path target, String actionDigest) {
        CommandResult run() throws Exception {
            return new CommandRunner(root, root.resolve("home"), Duration.ofSeconds(10),
                    Map.of("PATH", bin + ":/usr/bin:/bin", "FAKE_ARTIFACTS", artifacts.toString()))
                    .run(root.resolve("scripts/ci/install-automation-tools.sh").toString(), target.toString());
        }
    }
}

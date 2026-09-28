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

class ComposeVerifierContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void validatesRenderedComposeWithSyntheticSecretsAndRejectsUnsafeRendering() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                [[ $1 == compose ]]
                shift
                [[ $1 == --env-file ]]
                env_file=$2
                shift 2
                [[ $1 == -f && $2 == compose.yaml && $3 == config && $4 == --format && $5 == json ]]
                parent=${env_file%/*}
                jq --arg parent "$parent" --arg mode "$(cat -- "${0%/*}/mode")" '
                  .services.app.volumes[0].source = ($parent + "/media") |
                  .secrets |= with_entries(.value.file = ($parent + "/" + .key)) |
                  if $mode == "unsafe" then .services.app.read_only = false else . end
                ' automation-tests/src/test/resources/compose/valid-runtime.json
                """);
        docker.toFile().setExecutable(true);
        assertThat(run(bin, "valid").status()).isZero();
        assertThat(run(bin, "unsafe").status()).isNotZero();
    }

    private CommandResult run(Path bin, String mode) throws Exception {
        Files.writeString(bin.resolve("mode"), mode);
        return new CommandRunner(repository, Files.createTempDirectory(temporary, "home-"), Duration.ofSeconds(10),
                Map.of("PATH", bin + ":/usr/bin:/bin"))
                .run(repository.resolve("scripts/ci/verify-compose.sh").toString());
    }
}

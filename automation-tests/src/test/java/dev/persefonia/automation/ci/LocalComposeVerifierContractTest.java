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

class LocalComposeVerifierContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void rendersTrackedExampleWithoutLocalEnvironmentAndRejectsUnsafeRendering() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                [[ $COMPOSE_DISABLE_ENV_FILE == 1 ]]
                [[ ${LOCAL_SHELL_POISON:-} == '' ]]
                [[ $1 == compose && $2 == --env-file && $3 == .env.example ]]
                [[ $4 == -f && $5 == compose.yaml ]]
                fixture=automation-tests/src/test/resources/compose/valid-local-development.json
                if [[ $6 == -f ]]; then
                  [[ $7 == compose.override.example.yaml && $8 == config && $9 == --format && ${10} == json ]]
                  fixture=automation-tests/src/test/resources/compose/valid-local-development-override.json
                else
                  [[ $6 == config && $7 == --format && $8 == json ]]
                fi
                jq --arg workspace "$PWD" --arg mode "$(cat -- "${0%/*}/mode")" --arg fixture_name "$fixture" '
                  .services.app.volumes[0].source = $workspace |
                  .services.redis.volumes[0].source = ($workspace + "/scripts/local-redis-entrypoint.sh") |
                  .secrets |= with_entries(.value.file |= sub("^/synthetic/workspace"; $workspace)) |
                  if $mode == "unsafe" then .services.app.read_only = false
                  elif $mode == "optional-unsafe" and ($fixture_name | endswith("override.json"))
                    then .services.app.environment.PERSEFONIA_CACHE_PURGE_PROVIDER = "CLOUDFLARE"
                  else . end
                ' "$fixture"
                """);
        docker.toFile().setExecutable(true);
        assertThat(run(bin, "valid").status()).isZero();
        assertThat(run(bin, "unsafe").status()).isNotZero();
        assertThat(run(bin, "optional-unsafe").status()).isNotZero();
    }

    private CommandResult run(Path bin, String mode) throws Exception {
        Files.writeString(bin.resolve("mode"), mode);
        return new CommandRunner(repository, Files.createTempDirectory(temporary, "home-"), Duration.ofSeconds(10),
                Map.of("PATH", bin + ":/usr/bin:/bin", "LOCAL_SHELL_POISON", "must-not-pass"))
                .run(repository.resolve("scripts/ci/verify-local-compose.sh").toString());
    }
}

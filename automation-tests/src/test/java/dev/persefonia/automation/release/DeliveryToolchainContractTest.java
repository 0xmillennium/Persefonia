package dev.persefonia.automation.release;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliveryToolchainContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void everyEffectiveBuilderNodeMustReportThePinnedBuildKitVersion() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ $1 == buildx && ${2:-} == version ]]; then
                  printf 'github.com/docker/buildx v0.37.1 synthetic\\n'
                else
                  printf 'synthetic Docker\\n'
                fi
                """);
        docker.toFile().setExecutable(true);
        run(bin, "[{\"name\":\"a\",\"buildkit\":\"v0.33.0\"},{\"name\":\"b\",\"buildkit\":\"v0.33.0\"}]").requireSuccess();
        assertThat(run(bin, "[]").status()).isNotZero();
        assertThat(run(bin, "[{\"name\":\"a\",\"buildkit\":\"v0.33.0\"},{\"name\":\"b\",\"buildkit\":\"v0.32.0\"}]").status()).isNotZero();
        assertThat(run(bin, "malformed").status()).isNotZero();
    }

    private CommandResult run(Path bin, String nodes) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10),
                Map.of("PATH", bin + ":/usr/bin:/bin"))
                .run(repository.resolve("scripts/release/verify-delivery-toolchain.sh").toString(),
                        "v0.37.1", "v0.33.0", nodes);
    }
}

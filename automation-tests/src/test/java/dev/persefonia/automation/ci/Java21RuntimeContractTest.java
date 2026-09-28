package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import dev.persefonia.automation.support.InvocationLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Java21RuntimeContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void verifiesChecksumJavaVersionReadinessAndPublicRobots() throws Exception {
        Path jar = jar();
        Path bin = fakes();
        CommandResult good = run(bin, jar, "21", "running");
        good.requireSuccess();
        assertThat(good.stdout()).contains("Readiness endpoint verified", "Public /robots.txt smoke verified");
        assertThat(InvocationLog.read(temporary.resolve("curl.log")).toString())
                .contains("/actuator/health/readiness", "/robots.txt");
    }

    @Test
    void rejectsChecksumMismatchWrongJavaAndEarlyExit() throws Exception {
        Path jar = jar();
        Path bin = fakes();
        assertThat(run(bin, jar, "25", "running").status()).isNotZero();
        assertThat(run(bin, jar, "21", "exit").status()).isNotZero();
        assertThat(run(bin, jar, "21", "running", "robots").status()).isNotZero();
        Files.writeString(jar, "tampered");
        assertThat(run(bin, jar, "21", "running").status()).isNotZero();
    }

    private Path jar() throws Exception {
        Path jar = temporary.resolve("persefonia.jar");
        Files.writeString(jar, "synthetic jar");
        CommandResult digest = new CommandRunner(temporary, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                .run("sha256sum", "persefonia.jar");
        digest.requireSuccess();
        Files.writeString(temporary.resolve("persefonia.jar.sha256"), digest.stdout());
        return jar;
    }

    private Path fakes() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path java = bin.resolve("java");
        Files.writeString(java, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ $1 == -version ]]; then
                  printf 'openjdk version "%s.0.1"\\n' "$FAKE_JAVA_VERSION" >&2
                elif [[ ${FAKE_JAVA_MODE:-} == exit ]]; then
                  exit 0
                else
                  exec sleep 60
                fi
                """);
        java.toFile().setExecutable(true);
        Path curl = bin.resolve("curl");
        Files.writeString(curl, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ ${FAKE_JAVA_MODE:-} == exit ]]; then sleep 0.1; fi
                printf '%s\\0' "$#" "$@" >> "$FAKE_CURL_LOG"
                if [[ ${FAKE_CURL_FAIL:-} == robots && ${@: -1} == */robots.txt ]]; then exit 22; fi
                """);
        curl.toFile().setExecutable(true);
        return bin;
    }

    private CommandResult run(Path bin, Path jar, String version, String mode) throws Exception {
        return run(bin, jar, version, mode, "none");
    }

    private CommandResult run(Path bin, Path jar, String version, String mode, String curlFailure) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(15),
                Map.of("PATH", bin + ":/usr/bin:/bin", "FAKE_JAVA_VERSION", version,
                        "FAKE_JAVA_MODE", mode, "FAKE_CURL_FAIL", curlFailure,
                        "FAKE_CURL_LOG", temporary.resolve("curl.log").toString(),
                        "JAVA21_RUNTIME_LOG", temporary.resolve("runtime.log").toString()))
                .run(repository.resolve("scripts/ci/verify-java21-runtime.sh").toString(), jar.toString());
    }
}

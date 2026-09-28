package dev.persefonia.automation.release;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import dev.persefonia.automation.support.InvocationLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeSmokeContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final String image = "ghcr.io/example/persefonia@sha256:" + "a".repeat(64);

    @Test
    void startsDependenciesAndAppThenCleansUpAfterPublicSmoke() throws Exception {
        Path bin = fakes();
        CommandResult result = run(bin, "success");
        result.requireSuccess();
        List<List<String>> calls = InvocationLog.read(temporary.resolve("docker.log"));
        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("pull", "--platform", "linux/amd64", image));
        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("network", "create"));
        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("run", "--detach"));
        assertThat(calls.stream().filter(call -> call.getFirst().equals("run"))).hasSize(3);
        assertThat(calls.stream().filter(call -> call.getFirst().equals("rm"))).hasSize(3);
        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("network", "rm"));
        assertThat(InvocationLog.read(temporary.resolve("curl.log")).toString())
                .contains("/actuator/health/readiness", "/robots.txt");
    }

    @Test
    void failureStillCollectsLogsAndRemovesContainersAndNetwork() throws Exception {
        Path bin = fakes();
        CommandResult result = run(bin, "robots");
        assertThat(result.status()).isNotZero();
        assertThat(result.stderr()).contains("Candidate container logs", "PostgreSQL logs", "Redis logs");
        List<List<String>> calls = InvocationLog.read(temporary.resolve("docker.log"));
        assertThat(calls.stream().filter(call -> call.getFirst().equals("logs"))).hasSize(3);
        assertThat(calls.stream().filter(call -> call.getFirst().equals("rm"))).hasSize(3);
        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("network", "rm"));
    }

    private Path fakes() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\0' "$#" "$@" >> "$FAKE_DOCKER_LOG"
                case "$1 $2" in
                  'image inspect') printf 'amd64\\n' ;;
                  'exec '*) if [[ $* == *redis-cli* ]]; then printf 'PONG\\n'; fi ;;
                  'port '*) if [[ $* == *9001/tcp* ]]; then printf '127.0.0.1:19001\\n'; else printf '127.0.0.1:18080\\n'; fi ;;
                  'inspect --format') printf 'true\\n' ;;
                  'logs '*) printf 'synthetic logs\\n' ;;
                esac
                """);
        docker.toFile().setExecutable(true);
        Path curl = bin.resolve("curl");
        Files.writeString(curl, """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\0' "$#" "$@" >> "$FAKE_CURL_LOG"
                if [[ $FAKE_CURL_FAIL == robots && ${@: -1} == */robots.txt ]]; then exit 22; fi
                """);
        curl.toFile().setExecutable(true);
        Path uname = bin.resolve("uname");
        Files.writeString(uname, "#!/usr/bin/env bash\nprintf 'x86_64\\n'\n");
        uname.toFile().setExecutable(true);
        return bin;
    }

    private CommandResult run(Path bin, String mode) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(15),
                Map.of("PATH", bin + ":/usr/bin:/bin", "FAKE_DOCKER_LOG", temporary.resolve("docker.log").toString(),
                        "FAKE_CURL_LOG", temporary.resolve("curl.log").toString(), "FAKE_CURL_FAIL", mode))
                .run(repository.resolve("scripts/release/smoke-container-image.sh").toString(), image, "linux/amd64");
    }
}

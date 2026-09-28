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

class SourceAliasContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final String image = "ghcr.io/example/persefonia";
    private final String digest = "sha256:" + "a".repeat(64);
    private final String source = "b".repeat(40);

    @Test
    void publishesAbsentAliasOnlyAfterQualifiedDigestExists() throws Exception {
        Path bin = fakes();
        CommandResult result = run(bin, "absent", true);
        result.requireSuccess();
        assertThat(result.stdout()).contains("Published write-once source alias");
        List<List<String>> calls = InvocationLog.read(temporary.resolve("docker.log"));
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst()).startsWith("buildx", "imagetools", "create", "--prefer-index=false", "--tag",
                image + ":sha-" + source, image + "@" + digest);
        List<List<String>> requests = InvocationLog.read(temporary.resolve("curl.log"));
        assertThat(requests).anySatisfy(call -> assertThat(call).contains("scope=repository:example/persefonia:pull"));
        assertThat(requests).anySatisfy(call -> assertThat(call).contains("Authorization: Bearer synthetic"));
    }

    @Test
    void correctAliasIsIdempotentAndConflictingAliasFailsClosed() throws Exception {
        Path bin = fakes();
        assertThat(run(bin, "correct", true).status()).isZero();
        assertThat(run(bin, "conflict", true).status()).isNotZero();
        assertThat(InvocationLog.read(temporary.resolve("docker.log"))).isEmpty();
    }

    @Test
    void requiresRegistryAuthenticationBeforeMutation() throws Exception {
        Path bin = fakes();
        assertThat(run(bin, "absent", false).status()).isNotZero();
        assertThat(InvocationLog.read(temporary.resolve("docker.log"))).isEmpty();
    }

    @Test
    void rejectsUntrustedRegistryChallengeBeforeMutation() throws Exception {
        Path bin = fakes();
        assertThat(run(bin, "bad-challenge", true).status()).isNotZero();
        assertThat(InvocationLog.read(temporary.resolve("docker.log"))).isEmpty();
    }

    private Path fakes() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\0' "$@" >> "$FAKE_DOCKER_LOG"
                printf '\\0' >> "$FAKE_DOCKER_LOG"
                : > "$FAKE_ALIAS_MARKER"
                """);
        docker.toFile().setExecutable(true);
        Path curl = bin.resolve("curl");
        Files.writeString(curl, """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\0' "$@" >> "$FAKE_CURL_LOG"
                printf '\\0' >> "$FAKE_CURL_LOG"
                headers=
                url=
                method=GET
                while (($#)); do
                  case $1 in
                    --dump-header) headers=$2; shift 2 ;;
                    --output|--write-out|--header|--user|--data-urlencode|--max-time) shift 2 ;;
                    --head) method=HEAD; shift ;;
                    https://*) url=$1; shift ;;
                    *) shift ;;
                  esac
                done
                if [[ $url == https://ghcr.io/v2/ ]]; then
                  if [[ $FAKE_ALIAS_MODE == bad-challenge ]]; then
                    printf 'www-authenticate: Bearer realm="https://other.invalid/token",service="ghcr.io"\\n' > "$headers"
                  else
                    printf 'www-authenticate: Bearer realm="https://ghcr.io/token",service="ghcr.io"\\n' > "$headers"
                  fi
                  printf '401'
                elif [[ $url == https://ghcr.io/token ]]; then
                  printf '{"token":"synthetic"}'
                elif [[ $method == HEAD && $url == *"$FAKE_DIGEST" ]]; then
                  printf 'Docker-Content-Digest: %s\\n' "$FAKE_DIGEST" > "$headers"
                  printf '200'
                elif [[ $method == HEAD ]]; then
                  if [[ $FAKE_ALIAS_MODE == conflict ]]; then
                    printf 'Docker-Content-Digest: sha256:%s\\n' "$(printf 'c%.0s' {1..64})" > "$headers"
                    printf '200'
                  elif [[ $FAKE_ALIAS_MODE == correct || -f $FAKE_ALIAS_MARKER ]]; then
                    printf 'Docker-Content-Digest: %s\\n' "$FAKE_DIGEST" > "$headers"
                    printf '200'
                  else
                    : > "$headers"
                    printf '404'
                  fi
                fi
                """);
        curl.toFile().setExecutable(true);
        return bin;
    }

    private CommandResult run(Path bin, String mode, boolean credentials) throws Exception {
        Path config = temporary.resolve("docker-config");
        Files.createDirectories(config);
        if (credentials) {
            Files.writeString(config.resolve("config.json"), "{\"auths\":{\"ghcr.io\":{\"auth\":\"dXNlcjpwYXNz\"}}}");
        }
        Files.deleteIfExists(temporary.resolve("alias-created"));
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10),
                Map.of("PATH", bin + ":/usr/bin:/bin", "DOCKER_CONFIG", config.toString(),
                        "FAKE_ALIAS_MODE", mode, "FAKE_ALIAS_MARKER", temporary.resolve("alias-created").toString(),
                        "FAKE_DOCKER_LOG", temporary.resolve("docker.log").toString(),
                        "FAKE_CURL_LOG", temporary.resolve("curl.log").toString(), "FAKE_DIGEST", digest))
                .run(repository.resolve("scripts/release/publish-source-alias.sh").toString(), image, digest, source);
    }
}

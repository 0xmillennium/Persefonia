package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class PreflightTestSupport {
    static final String IMAGE_REFERENCE = "ghcr.io/0xmillennium/persefonia@sha256:" + "a".repeat(64);
    private static final Path PREFLIGHT = Path.of("../scripts/deploy/preflight.sh").toAbsolutePath();

    private PreflightTestSupport() {
    }

    static Result run(Path temporaryDirectory, Map<String, String> overrides) throws Exception {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "preflight-");
        Path bin = Files.createDirectory(fixture.resolve("bin"));
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/bin/sh
                set -eu
                printf '%s\\n' "$*" >> "$PREFLIGHT_TEST_DOCKER_CALLS"
                [ "$*" = 'compose version --short' ] || exit 99
                [ "$PREFLIGHT_TEST_PLUGIN_MISSING" = false ] || exit 1
                printf '%s\\n' "$PREFLIGHT_TEST_COMPOSE_VERSION"
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
        Path calls = fixture.resolve("docker-calls");
        ProcessBuilder builder = new ProcessBuilder(PREFLIGHT.toString()).redirectErrorStream(true);
        String searchPath = builder.environment().get("PATH");
        builder.environment().clear();
        builder.environment().put("PATH", bin + ":" + searchPath);
        builder.environment().put("PERSEFONIA_ENV_FILE", fixture.resolve("missing.env").toString());
        builder.environment().put("PERSEFONIA_IMAGE_REF", IMAGE_REFERENCE);
        builder.environment().put("PREFLIGHT_TEST_DOCKER_CALLS", calls.toString());
        builder.environment().put("PREFLIGHT_TEST_COMPOSE_VERSION", "2.39.4");
        builder.environment().put("PREFLIGHT_TEST_PLUGIN_MISSING", "false");
        builder.environment().putAll(overrides);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output, Files.exists(calls) ? Files.readString(calls) : "");
    }

    record Result(int status, String output, String dockerCalls) {}
}

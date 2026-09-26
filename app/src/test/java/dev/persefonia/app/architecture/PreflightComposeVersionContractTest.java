package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightComposeVersionContractTest {
    private static final Path PREFLIGHT = Path.of("../scripts/deploy/preflight.sh").toAbsolutePath();
    private static final String MINIMUM_VERSION_ERROR =
            "Docker Compose 2.33.1 or newer is required by the production runtime";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsOlderVersionsBeforeRuntimeFilesOrComposeRendering() throws Exception {
        for (String version : List.of("2.24.0", "2.33.0", "2.9.9", "1.99.99")) {
            Result result = preflight(version, false);

            assertThat(result.status()).as(version).isNotZero();
            assertThat(result.output()).contains(MINIMUM_VERSION_ERROR, "found " + version)
                    .doesNotContain("required file", "configuration validation failed");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void acceptsMinimumAndNewerVersionsUsingNumericComparison() throws Exception {
        for (String version : List.of("2.33.1", "2.39.4", "3.0.0", "v2.39.4", "2.100.0")) {
            Result result = preflight(version, false);

            // A deliberately absent env file stops preflight before real secrets or host resources.
            assertThat(result.status()).as(version).isNotZero();
            assertThat(result.output()).contains("required file is missing or unreadable:", "missing.env")
                    .doesNotContain(MINIMUM_VERSION_ERROR, "cannot parse");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void rejectsUnparseableVersionsWithoutAssumingCompatibility() throws Exception {
        for (String version : List.of("unparseable", "", "2.33", "2.33.x", "2.39.4\n3.0.0", "\\062.33.1")) {
            Result result = preflight(version, false);

            assertThat(result.status()).as(version).isNotZero();
            assertThat(result.output()).contains("cannot parse Docker Compose version")
                    .doesNotContain("required file");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void reportsMissingComposePluginBeforeInspectingRuntimeFiles() throws Exception {
        Result result = preflight("", true);

        assertThat(result.status()).isNotZero();
        assertThat(result.output()).contains("Docker Compose plugin is unavailable")
                .doesNotContain("required file");
        assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
    }

    private Result preflight(String version, boolean pluginMissing) throws Exception {
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
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("PERSEFONIA_ENV_FILE", fixture.resolve("missing.env").toString());
        builder.environment().put("PREFLIGHT_TEST_DOCKER_CALLS", calls.toString());
        builder.environment().put("PREFLIGHT_TEST_COMPOSE_VERSION", version);
        builder.environment().put("PREFLIGHT_TEST_PLUGIN_MISSING", Boolean.toString(pluginMissing));
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output, Files.readString(calls));
    }

    private record Result(int status, String output, String dockerCalls) {}
}

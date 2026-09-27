package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightComposeVersionContractTest {
    private static final String MINIMUM_VERSION_ERROR =
            "Docker Compose 2.33.1 or newer is required by the production runtime";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsOlderVersionsBeforeRuntimeFilesOrComposeRendering() throws Exception {
        for (String version : List.of("2.24.0", "2.33.0", "2.9.9", "1.99.99")) {
            var result = preflight(version, false);

            assertThat(result.status()).as(version).isNotZero();
            assertThat(result.output()).contains(MINIMUM_VERSION_ERROR, "found " + version)
                    .doesNotContain("required file", "configuration validation failed");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void acceptsMinimumAndNewerVersionsUsingNumericComparison() throws Exception {
        for (String version : List.of("2.33.1", "2.39.4", "3.0.0", "v2.39.4", "2.100.0")) {
            var result = preflight(version, false);

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
            var result = preflight(version, false);

            assertThat(result.status()).as(version).isNotZero();
            assertThat(result.output()).contains("cannot parse Docker Compose version")
                    .doesNotContain("required file");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void reportsMissingComposePluginBeforeInspectingRuntimeFiles() throws Exception {
        var result = preflight("", true);

        assertThat(result.status()).isNotZero();
        assertThat(result.output()).contains("Docker Compose plugin is unavailable")
                .doesNotContain("required file");
        assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
    }

    private PreflightTestSupport.Result preflight(String version, boolean pluginMissing) throws Exception {
        return PreflightTestSupport.run(temporaryDirectory, Map.of(
                "PREFLIGHT_TEST_COMPOSE_VERSION", version,
                "PREFLIGHT_TEST_PLUGIN_MISSING", Boolean.toString(pluginMissing)));
    }
}

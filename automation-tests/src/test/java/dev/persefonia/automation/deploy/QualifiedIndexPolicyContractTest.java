package dev.persefonia.automation.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualifiedIndexPolicyContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final Path valid = repository.resolve("automation-tests/src/test/resources/registry/valid-index.json");

    @Test
    void acceptsExactlyBothQualifiedPlatforms() throws Exception {
        policy(valid).requireSuccess();
    }

    @Test
    void rejectsMissingDuplicateUnexpectedOrMalformedRuntimeDescriptors() throws Exception {
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("not-array", ".manifests = {}"),
                Map.entry("missing", "del(.manifests[1])"),
                Map.entry("duplicate", ".manifests[1].platform.architecture = \"amd64\""),
                Map.entry("unexpected", ".manifests[1].platform.architecture = \"riscv64\""),
                Map.entry("invalid-digest", ".manifests[1].digest = \"sha256:bad\""),
                Map.entry("invalid-media", ".manifests[1].mediaType = \"other\""),
                Map.entry("partial-os", ".manifests[2].platform.architecture = \"amd64\""),
                Map.entry("partial-architecture", ".manifests[2].platform.os = \"linux\""),
                Map.entry("missing-os", "del(.manifests[2].platform.os)"),
                Map.entry("missing-architecture", "del(.manifests[2].platform.architecture)"),
                Map.entry("malformed-platform", ".manifests[2].platform = []"),
                Map.entry("missing-platform", "del(.manifests[1].platform)"));
        for (var entry : invalid.entrySet()) {
            Path variant = temporary.resolve(entry.getKey() + ".json");
            CommandResult mutation = runner().run("jq", entry.getValue(), valid.toString());
            mutation.requireSuccess();
            Files.writeString(variant, mutation.stdout());
            assertThat(policy(variant).status()).as(entry.getKey()).isNotZero();
        }
    }

    private CommandRunner runner() {
        return new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of());
    }

    private CommandResult policy(Path input) throws Exception {
        return runner().run("jq", "-e", "--argjson", "expected", "[\"linux/amd64\",\"linux/arm64\"]",
                "-f", repository.resolve("scripts/deploy/qualified-index-policy.jq").toString(), input.toString());
    }
}

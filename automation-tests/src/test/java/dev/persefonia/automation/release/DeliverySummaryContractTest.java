package dev.persefonia.automation.release;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliverySummaryContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final String source = "a".repeat(40);
    private final String image = "ghcr.io/example/persefonia";

    @Test
    void rendersDeterministicSummaryWithoutNetworkOrRepositoryMutation() throws Exception {
        Path summary = temporary.resolve("summary.md");
        Files.writeString(summary, "preface\n");
        run(inputs(), summary).requireSuccess();
        String rendered = Files.readString(summary);
        assertThat(rendered).startsWith("preface\n## Delivery\n");
        assertThat(rendered).contains(source, image, "BuildKit provenance", "GitHub signed provenance");
        Path second = temporary.resolve("second.md");
        run(inputs(), second).requireSuccess();
        assertThat(rendered.substring("preface\n".length())).isEqualTo(Files.readString(second));
    }

    @Test
    void malformedOrInconsistentInputsNeverWriteSummary() throws Exception {
        Path summary = temporary.resolve("summary.md");
        Map<String, String> missing = inputs();
        missing.remove("DELIVERY_IMAGE_DIGEST");
        assertThat(run(missing, summary).status()).isNotZero();
        assertThat(Files.exists(summary)).isFalse();
        Map<String, String> inconsistent = inputs();
        inconsistent.put("DELIVERY_SOURCE_ALIAS", image + ":sha-" + "b".repeat(40));
        assertThat(run(inconsistent, summary).status()).isNotZero();
        assertThat(Files.exists(summary)).isFalse();
    }

    private Map<String, String> inputs() {
        Map<String, String> values = new HashMap<>();
        values.put("DELIVERY_SOURCE_SHA", source);
        values.put("DELIVERY_SOURCE_URL", "https://github.com/example/persefonia");
        values.put("DELIVERY_APPLICATION_VERSION", "0.1.0");
        values.put("DELIVERY_IMAGE_NAME", image);
        values.put("DELIVERY_IMAGE_DIGEST", "sha256:" + "b".repeat(64));
        values.put("DELIVERY_SOURCE_ALIAS", image + ":sha-" + source);
        values.put("DELIVERY_BUILDX_VERSION", "v0.37.1");
        values.put("DELIVERY_BUILDKIT_VERSION", "v0.33.0");
        values.put("DELIVERY_BUILDKIT_IMAGE", "moby/buildkit@sha256:" + "c".repeat(64));
        return values;
    }

    private CommandResult run(Map<String, String> values, Path summary) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10), values)
                .run(repository.resolve("scripts/release/write-delivery-summary.sh").toString(), summary.toString());
    }
}

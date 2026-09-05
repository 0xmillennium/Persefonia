package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryWorkflowArchitectureTest {
    private static final Path DELIVERY_WORKFLOW = Path.of("../.github/workflows/delivery.yml");
    private static final Path SUPPORTED_PLATFORMS = Path.of("../docker/supported-platforms.txt");
    private static final Path BOOTJAR_VERIFIER = Path.of("../scripts/ci/verify-bootjar.sh");

    @Test
    void deliveryConsumesTheVerifiedArtifactWithoutApplicationBuildTooling() throws Exception {
        String workflow = Files.readString(DELIVERY_WORKFLOW);

        assertThat(workflow)
                .doesNotContain("./gradlew")
                .doesNotContain("setup-gradle")
                .doesNotContain("setup-java")
                .doesNotContain("setup-node")
                .doesNotContain("npm")
                .doesNotContain("compileJava")
                .doesNotContain(":app:bootJar")
                .doesNotContain("D4")
                .doesNotContain("Delivery Step 4");
    }

    @Test
    void deliveryUsesTheCommittedTwoPlatformNativeVerificationMapping() throws Exception {
        String workflow = Files.readString(DELIVERY_WORKFLOW);

        assertThat(workflow)
                .contains("platform: linux/amd64")
                .contains("runner: ubuntu-24.04")
                .contains("platform: linux/arm64")
                .contains("runner: ubuntu-24.04-arm");
        assertThat(Files.readAllLines(SUPPORTED_PLATFORMS)).containsExactly("linux/amd64", "linux/arm64");
    }

    @Test
    void deliveryExplicitlyProducesSlsaV1BuildKitProvenance() throws Exception {
        assertThat(Files.readString(DELIVERY_WORKFLOW)).contains("provenance: mode=max,version=v1");
    }

    @Test
    void bootJarStagingRemovesOnlyItsOwnOutputs() throws Exception {
        String verifier = Files.readString(BOOTJAR_VERIFIER);

        assertThat(verifier)
                .doesNotContain("rm -rf")
                .contains("rm -f -- \"$staged_bootjar\" \"$staged_checksum\"");
    }
}

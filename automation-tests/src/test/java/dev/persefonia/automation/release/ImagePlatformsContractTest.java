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

class ImagePlatformsContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final Path valid = repository.resolve("automation-tests/src/test/resources/registry/valid-index.json");

    @Test
    void requiresExactRunnablePlatformsThroughBuildxAndManifestFallback() throws Exception {
        Path bin = fakeDocker();
        assertThat(run(bin, valid, "exact", false).status()).isZero();
        assertThat(run(bin, valid, "exact", true).status()).isZero();
        Path extra = mutate(".manifests[1].platform.architecture = \"riscv64\"");
        assertThat(run(bin, extra, "exact", false).status()).isNotZero();
        assertThat(run(bin, extra, "contains", false).status()).isNotZero();
        Path added = mutate(".manifests += [{mediaType: \"application/vnd.oci.image.manifest.v1+json\", digest: \"sha256:" + "c".repeat(64) + "\", platform: {os: \"linux\", architecture: \"riscv64\"}}]");
        assertThat(run(bin, added, "exact", false).status()).isNotZero();
        assertThat(run(bin, added, "contains", false).status()).isZero();
    }

    @Test
    void rejectsDuplicateOrMalformedRuntimeDescriptors() throws Exception {
        Path bin = fakeDocker();
        for (String change : new String[] {
                ".manifests[1].platform.architecture = \"amd64\"",
                ".manifests[1].digest = \"sha256:bad\"",
                ".manifests[1].mediaType = \"unexpected\"",
                ".manifests[1].platform = \"linux/arm64\"",
                ".manifests = {}"}) {
            assertThat(run(bin, mutate(change), "exact", false).status()).as(change).isNotZero();
        }
        assertThat(run(bin, mutate(".manifests[1].platform.architecture = \"amd64\""), "contains", false)
                .status()).isNotZero();
    }

    @Test
    void containsModeAcceptsDistinctVariantsButRejectsDuplicateVariant() throws Exception {
        Path bin = fakeDocker();
        Path variants = mutate(".manifests += [" +
                "{mediaType: \"application/vnd.oci.image.manifest.v1+json\", digest: \"sha256:" + "c".repeat(64) +
                "\", platform: {os: \"linux\", architecture: \"arm\", variant: \"v6\"}}," +
                "{mediaType: \"application/vnd.oci.image.manifest.v1+json\", digest: \"sha256:" + "d".repeat(64) +
                "\", platform: {os: \"linux\", architecture: \"arm\", variant: \"v7\"}}]");
        assertThat(run(bin, variants, "contains", false).status()).isZero();
        assertThat(run(bin, variants, "exact", false).status()).isNotZero();
        Path duplicateVariant = mutate(".manifests[0].platform.variant = \"v6\" | " +
                ".manifests += [{mediaType: \"application/vnd.oci.image.manifest.v1+json\", " +
                "digest: \"sha256:" + "c".repeat(64) +
                "\", platform: {os: \"linux\", architecture: \"amd64\", variant: \"v6\"}}]");
        assertThat(run(bin, duplicateVariant, "contains", false).status()).isNotZero();
        assertThat(run(bin, mutate(".manifests[0].platform.variant = 6"), "contains", false)
                .status()).isNotZero();
    }

    @Test
    void rejectsPartialPlatformIdentitiesInBothModes() throws Exception {
        Path bin = fakeDocker();
        for (String change : new String[] {
                ".manifests[2].platform.architecture = \"amd64\"",
                ".manifests[2].platform.os = \"linux\"",
                "del(.manifests[2].platform.os)",
                "del(.manifests[2].platform.architecture)"}) {
            Path index = mutate(change);
            for (String mode : new String[] {"exact", "contains"}) {
                assertThat(run(bin, index, mode, false).status()).as(change + " " + mode).isNotZero();
            }
        }
    }

    @Test
    void releaseAndDeploymentAgreeOnDescriptorClassification() throws Exception {
        Path bin = fakeDocker();
        for (Path index : new Path[] {valid,
                mutate(".manifests[2].platform.architecture = \"amd64\""),
                mutate(".manifests[2].platform.os = \"linux\""),
                mutate("del(.manifests[2].platform.os)"),
                mutate("del(.manifests[2].platform.architecture)"),
                mutate(".manifests[2].mediaType = \"invalid\""),
                mutate(".manifests[2].digest = \"sha256:bad\""),
                mutate(".manifests[2].platform = []")}) {
            boolean releaseAccepted = run(bin, index, "exact", false).status() == 0;
            boolean deploymentAccepted = new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                    .run("jq", "-e", "--argjson", "expected", "[\"linux/amd64\",\"linux/arm64\"]",
                            "-f", repository.resolve("scripts/deploy/qualified-index-policy.jq").toString(), index.toString())
                    .status() == 0;
            assertThat(releaseAccepted).as(index.toString()).isEqualTo(deploymentAccepted);
        }
    }

    private Path mutate(String filter) throws Exception {
        CommandResult result = new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                .run("jq", filter, valid.toString());
        result.requireSuccess();
        Path path = Files.createTempFile(temporary, "index-", ".json");
        Files.writeString(path, result.stdout());
        return path;
    }

    private Path fakeDocker() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ $1 == buildx && ${2:-} == version ]]; then
                  [[ ${FAKE_MANIFEST_FALLBACK:-0} == 0 ]]
                else
                  cat -- "$FAKE_INDEX"
                fi
                """);
        docker.toFile().setExecutable(true);
        return bin;
    }

    private CommandResult run(Path bin, Path index, String mode, boolean fallback) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10),
                Map.of("PATH", bin + ":/usr/bin:/bin", "FAKE_INDEX", index.toString(),
                        "FAKE_MANIFEST_FALLBACK", fallback ? "1" : "0"))
                .run(repository.resolve("scripts/release/verify-image-platforms.sh").toString(),
                        "example.invalid/image@sha256:" + "a".repeat(64),
                        repository.resolve("docker/supported-platforms.txt").toString(), mode);
    }
}

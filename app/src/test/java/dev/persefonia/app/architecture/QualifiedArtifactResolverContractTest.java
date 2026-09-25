package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualifiedArtifactResolverContractTest {
    private static final Path RESOLVER = Path.of("../scripts/deploy/resolve-qualified-artifact.sh");
    private static final String RESOURCE_ROOT = "/architecture/rc-deployment/";
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String REPOSITORY = "0xmillennium/Persefonia";
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia";

    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsOnlyFiveVerifiedHandoffRecordsAndUsesExactSignerPolicy() throws Exception {
        Resolution result = resolve("valid-index.json", Map.of());

        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo("source_sha=" + SOURCE_SHA + "\n"
                + "image_name=" + IMAGE + "\n"
                + "image_digest=" + result.digest() + "\n"
                + "image_reference=" + IMAGE + "@" + result.digest() + "\n"
                + "source_alias=" + IMAGE + ":sha-" + SOURCE_SHA + "\n");
        assertThat(result.requests())
                .contains("GET https://ghcr.io/token")
                .contains("HEAD https://ghcr.io/v2/0xmillennium/persefonia/manifests/sha-" + SOURCE_SHA)
                .contains("GET https://ghcr.io/v2/0xmillennium/persefonia/manifests/" + result.digest());
        assertThat(result.ghArguments())
                .contains("oci://" + IMAGE + "@" + result.digest())
                .contains("--bundle-from-oci", "--repo\n" + REPOSITORY,
                        "--predicate-type\nhttps://slsa.dev/provenance/v1",
                        "--source-digest\n" + SOURCE_SHA,
                        "--source-ref\nrefs/heads/master",
                        "--signer-digest\n" + SOURCE_SHA,
                        "--signer-workflow\n" + REPOSITORY + "/.github/workflows/delivery.yml",
                        "--deny-self-hosted-runners");
    }

    @Test
    void rejectsInvalidIdentityBeforeRegistryAccess() throws Exception {
        for (Map<String, String> options : List.of(
                Map.of("SOURCE_SHA_OVERRIDE", "abc"),
                Map.of("SOURCE_SHA_OVERRIDE", "A".repeat(40)),
                Map.of("REPOSITORY_OVERRIDE", "another/repository/extra"),
                Map.of("EXTRA_ARGUMENT", IMAGE))) {
            Resolution result = resolve("valid-index.json", options);
            assertThat(result.status()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.requests()).isEmpty();
        }
    }

    @Test
    void distinguishesAbsentAliasFromOtherRegistryFailures() throws Exception {
        for (String status : List.of("404", "401", "403", "429", "500")) {
            Resolution result = resolve("valid-index.json", Map.of("FAKE_ALIAS_STATUS", status));
            assertThat(result.status()).as("HTTP %s", status).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.ghArguments()).isEmpty();
            if (status.equals("404")) {
                assertThat(result.stderr()).contains("source alias is absent");
            } else {
                assertThat(result.stderr()).contains("HTTP " + status).doesNotContain("alias is absent");
            }
        }
    }

    @Test
    void rejectsMalformedDigestAndChangedRawResponse() throws Exception {
        for (Map<String, String> options : List.of(
                Map.of("FAKE_ALIAS_DIGEST", "sha256:short"),
                Map.of("FAKE_INDEX_BODY", "{}"),
                Map.of("FAKE_INDEX_HEADER_DIGEST", "sha256:" + "c".repeat(64)))) {
            Resolution result = resolve("valid-index.json", options);
            assertThat(result.status()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.ghArguments()).isEmpty();
        }
    }

    @Test
    void rejectsNonIndexMissingDuplicateAndUnexpectedRuntimePlatforms() throws Exception {
        for (String fixture : List.of(
                "single-image-manifest.json",
                "missing-platform-index.json",
                "duplicate-platform-index.json",
                "unexpected-platform-index.json")) {
            Resolution result = resolve(fixture, Map.of());
            assertThat(result.status()).as(fixture).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.ghArguments()).isEmpty();
        }
    }

    @Test
    void doesNotEmitHandoffRecordsWhenSignedProvenanceFails() throws Exception {
        Resolution result = resolve("valid-index.json", Map.of("FAKE_GH_STATUS", "1"));

        assertThat(result.status()).isNotZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains("signed provenance verification failed");
    }

    private Resolution resolve(String fixtureName, Map<String, String> options) throws Exception {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "resolver-");
        Path binaryDirectory = Files.createDirectory(fixture.resolve("bin"));
        Path dockerDirectory = Files.createDirectory(fixture.resolve("docker"));
        Files.writeString(dockerDirectory.resolve("config.json"),
                "{\"auths\":{\"ghcr.io\":{\"auth\":\"Y2k6c2VjcmV0\"}}}");
        Path indexFile = fixture.resolve("index.json");
        copyResource(fixtureName, indexFile);
        if (options.containsKey("FAKE_INDEX_BODY")) {
            Files.writeString(indexFile, options.get("FAKE_INDEX_BODY"));
        }
        Path requests = fixture.resolve("requests");
        Path ghArguments = fixture.resolve("gh-arguments");
        copyResource("fake-curl.sh", binaryDirectory.resolve("curl"));
        copyResource("fake-gh.sh", binaryDirectory.resolve("gh"));
        assertThat(binaryDirectory.resolve("curl").toFile().setExecutable(true)).isTrue();
        assertThat(binaryDirectory.resolve("gh").toFile().setExecutable(true)).isTrue();

        byte[] expectedBody = resourceBytes(fixtureName);
        String digest = "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(expectedBody));
        ProcessBuilder command = new ProcessBuilder(
                RESOLVER.toAbsolutePath().toString(),
                options.getOrDefault("SOURCE_SHA_OVERRIDE", SOURCE_SHA),
                options.getOrDefault("REPOSITORY_OVERRIDE", REPOSITORY),
                Path.of("../docker/supported-platforms.txt").toAbsolutePath().toString());
        if (options.containsKey("EXTRA_ARGUMENT")) {
            command.command().add(options.get("EXTRA_ARGUMENT"));
        }
        command.directory(fixture.toFile());
        Map<String, String> environment = command.environment();
        environment.put("PATH", binaryDirectory + ":" + environment.get("PATH"));
        environment.put("DOCKER_CONFIG", dockerDirectory.toString());
        environment.put("FAKE_INDEX_FILE", indexFile.toString());
        environment.put("FAKE_DIGEST", digest);
        environment.put("FAKE_REQUESTS", requests.toString());
        environment.put("FAKE_GH_ARGS", ghArguments.toString());
        environment.putAll(options);
        Process process = command.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Resolution(process.waitFor(), stdout, stderr, digest,
                Files.exists(requests) ? Files.readString(requests) : "",
                Files.exists(ghArguments) ? Files.readString(ghArguments) : "");
    }

    private static void copyResource(String name, Path destination) throws IOException {
        try (InputStream source = resource(name)) {
            Files.copy(source, destination);
        }
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (InputStream source = resource(name)) {
            return source.readAllBytes();
        }
    }

    private static InputStream resource(String name) {
        InputStream source = QualifiedArtifactResolverContractTest.class.getResourceAsStream(RESOURCE_ROOT + name);
        if (source == null) {
            throw new IllegalStateException("Missing test resource: " + name);
        }
        return source;
    }

    private record Resolution(int status, String stdout, String stderr, String digest,
                              String requests, String ghArguments) {}
}

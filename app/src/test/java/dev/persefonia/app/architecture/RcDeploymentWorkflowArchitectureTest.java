package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RcDeploymentWorkflowArchitectureTest {
    private static final Path WORKFLOW = Path.of("../.github/workflows/deploy-rc.yml");
    private static final Path RESOLVER = Path.of("../scripts/deploy/resolve-qualified-artifact.sh");
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String CHILD_DIGEST = "sha256:" + "b".repeat(64);
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia";
    private static final String REPOSITORY = "0xmillennium/Persefonia";

    @TempDir
    Path temporaryDirectory;

    @Test
    void workflowAcceptsOnlyCurrentTrustedDeliveryCompletion() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow).startsWith("name: Deploy RC\n")
                .contains("  workflow_run:\n    workflows:\n      - Delivery\n    branches:\n      - master\n    types:\n      - completed")
                .doesNotContain("  push:", "  pull_request:", "  workflow_dispatch:");
        assertThat(workflow)
                .contains("github.event.workflow_run.conclusion == 'success'")
                .contains("github.event.workflow_run.event == 'workflow_run'")
                .contains("github.event.workflow_run.head_branch == 'master'")
                .contains("github.event.workflow_run.head_repository.full_name == github.repository")
                .contains("github.event.workflow_run.head_repository.fork == false")
                .contains("github.sha == github.event.workflow_run.head_sha")
                .contains("group: rc-deployment\n  cancel-in-progress: false")
                .doesNotContain("group: rc-deployment-${{");
    }

    @Test
    void workflowKeepsTheHandoffReadOnlyAndLeastPrivileged() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        int permissionStart = workflow.indexOf("    permissions:\n");
        int permissionEnd = workflow.indexOf("    outputs:\n", permissionStart);
        assertThat(permissionStart).isPositive();
        assertThat(permissionEnd).isGreaterThan(permissionStart);
        assertThat(workflow.substring(permissionStart, permissionEnd).lines()
                .map(String::trim).filter(line -> !line.isEmpty()).toList())
                .containsExactlyInAnyOrder("permissions:", "contents: read", "packages: read");

        assertThat(workflow).contains("permissions: {}")
                .doesNotContain("contents: write", "packages: write", "attestations: read",
                        "attestations: write", "id-token: write", "deployments: write",
                        "environment:", "actions/upload-artifact", "actions/download-artifact")
                .contains("ref: ${{ github.event.workflow_run.head_sha }}")
                .contains("persist-credentials: false")
                .contains("test \"$(git rev-parse HEAD)\" = \"$EXPECTED_SOURCE_SHA\"")
                .contains("GH_TOKEN: ${{ github.token }}")
                .contains("password: ${{ github.token }}")
                .contains("./scripts/deploy/resolve-qualified-artifact.sh")
                .contains("docker/supported-platforms.txt")
                .contains("image_reference=%s@%s")
                .doesNotContain("./gradlew", "setup-java", "setup-gradle", "setup-node", "npm", "vite",
                        "docker build", "docker compose", "ssh ", "scp ", "rsync ", "sftp ", "systemctl");
        assertThat(workflow.split(Pattern.quote("GH_TOKEN: ${{ github.token }}"), -1)).hasSize(2);
        assertThat(workflow.substring(workflow.indexOf("      - name: Resolve and verify qualified artifact")))
                .contains("GH_TOKEN: ${{ github.token }}");
        assertThat(Pattern.compile("(?m)^        uses: ([^\\s#]+)").matcher(workflow).results()
                .map(match -> match.group(1)).toList())
                .containsExactly(
                        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                        "docker/login-action@dbcb813823bdd20940b903addbd779551569679f");
        String jobs = workflow.substring(workflow.indexOf("\njobs:\n") + "\njobs:\n".length());
        assertThat(Pattern.compile("(?m)^  ([a-z][a-z-]+):\\s*$").matcher(jobs).results()
                .map(match -> match.group(1)).toList())
                .containsExactly("resolve-qualified-artifact");
    }

    @Test
    void resolverKeepsRegistryAndProvenanceVerificationReadOnly() throws Exception {
        String resolver = Files.readString(RESOLVER);

        assertThat(resolver)
                .contains("if [[ \"$#\" -ne 4 ]]", "source_alias=\"sha-${source_sha}\"")
                .contains("registry_request HEAD \"manifests/${source_alias}\"")
                .contains("registry_request GET \"manifests/${resolved_digest}\"")
                .contains("actual_hash=$(sha256sum -- \"$registry_body\")")
                .contains("jq -e --argjson expected \"$supported_platforms_json\"")
                .contains("--bundle-from-oci", "--repo \"$repository_slug\"")
                .contains("--predicate-type https://slsa.dev/provenance/v1")
                .contains("--source-digest \"$source_sha\"")
                .contains("--source-ref refs/heads/master")
                .contains("--signer-digest \"$source_sha\"")
                .contains("$repository_slug/.github/workflows/delivery.yml")
                .contains("--deny-self-hosted-runners")
                .doesNotContain("GITHUB_OUTPUT", "set -x", "--api", "--source-tag", "registry_request POST",
                        "registry_request PUT", "registry_request DELETE", "docker build");
        assertThat(resolver.indexOf("actual_hash=$(sha256sum"))
                .isLessThan(resolver.indexOf("jq -e --argjson expected"));
        assertThat(resolver.lastIndexOf("printf '%s\\n' \"$resolved_digest\""))
                .isGreaterThan(resolver.indexOf("gh attestation verify"));
    }

    @Test
    void resolverEmitsOnlyVerifiedIndexDigestAndUsesExactSignerPolicy() throws Exception {
        Resolution result = resolve(index("linux/amd64", "linux/arm64", "unknown/unknown"),
                Map.of());

        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(result.digest() + "\n");
        assertThat(result.requests())
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
    void resolverRejectsInvalidInputBeforeRegistryAccess() throws Exception {
        Resolution shortSha = resolve(index("linux/amd64", "linux/arm64"),
                Map.of("SOURCE_SHA_OVERRIDE", "abc"));
        assertThat(shortSha.status()).isNotZero();
        assertThat(shortSha.stdout()).isEmpty();
        assertThat(shortSha.requests()).isEmpty();

        Resolution wrongImage = resolve(index("linux/amd64", "linux/arm64"),
                Map.of("IMAGE_OVERRIDE", "ghcr.io/another/repository"));
        assertThat(wrongImage.status()).isNotZero();
        assertThat(wrongImage.stdout()).isEmpty();
        assertThat(wrongImage.requests()).isEmpty();
    }

    @Test
    void resolverDistinguishesAbsentAliasFromOtherRegistryFailures() throws Exception {
        for (String status : List.of("404", "401", "403", "429", "500")) {
            Resolution result = resolve(index("linux/amd64", "linux/arm64"),
                    Map.of("FAKE_ALIAS_STATUS", status));
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
    void resolverRejectsMalformedDigestAndChangedRawResponse() throws Exception {
        for (Map<String, String> options : List.of(
                Map.of("FAKE_ALIAS_DIGEST", "sha256:short"),
                Map.of("FAKE_INDEX_BODY", "{}"),
                Map.of("FAKE_INDEX_HEADER_DIGEST", "sha256:" + "c".repeat(64)))) {
            Resolution result = resolve(index("linux/amd64", "linux/arm64"), options);
            assertThat(result.status()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.ghArguments()).isEmpty();
        }
    }

    @Test
    void resolverRejectsNonIndexMissingDuplicateAndUnexpectedRuntimePlatforms() throws Exception {
        for (String manifest : List.of(
                "{\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"manifests\":[]}",
                index("linux/amd64"),
                index("linux/amd64", "linux/amd64", "linux/arm64"),
                index("linux/amd64", "linux/arm64", "linux/s390x"))) {
            Resolution result = resolve(manifest, Map.of());
            assertThat(result.status()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.ghArguments()).isEmpty();
        }
    }

    @Test
    void resolverDoesNotExposeDigestWhenSignedProvenanceFails() throws Exception {
        Resolution result = resolve(index("linux/amd64", "linux/arm64"),
                Map.of("FAKE_GH_STATUS", "1"));

        assertThat(result.status()).isNotZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains("signed provenance verification failed");
    }

    private Resolution resolve(String index, Map<String, String> options) throws Exception {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "resolver-");
        Path binaryDirectory = Files.createDirectory(fixture.resolve("bin"));
        Path dockerDirectory = Files.createDirectory(fixture.resolve("docker"));
        Files.writeString(dockerDirectory.resolve("config.json"),
                "{\"auths\":{\"ghcr.io\":{\"auth\":\"Y2k6c2VjcmV0\"}}}");
        Path indexFile = fixture.resolve("index.json");
        Files.writeString(indexFile, options.getOrDefault("FAKE_INDEX_BODY", index));
        Path requests = fixture.resolve("requests");
        Path ghArguments = fixture.resolve("gh-arguments");
        writeExecutable(binaryDirectory.resolve("curl"), FAKE_CURL);
        writeExecutable(binaryDirectory.resolve("gh"), FAKE_GH);

        String digest = "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(index.getBytes(StandardCharsets.UTF_8)));
        ProcessBuilder command = new ProcessBuilder(
                RESOLVER.toAbsolutePath().toString(),
                options.getOrDefault("IMAGE_OVERRIDE", IMAGE),
                options.getOrDefault("SOURCE_SHA_OVERRIDE", SOURCE_SHA),
                REPOSITORY,
                Path.of("../docker/supported-platforms.txt").toAbsolutePath().toString());
        command.directory(Path.of(".").toAbsolutePath().toFile());
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

    private static void writeExecutable(Path path, String contents) throws IOException {
        Files.writeString(path, contents);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }

    private static String index(String... platforms) {
        StringBuilder descriptors = new StringBuilder();
        for (String platform : platforms) {
            if (!descriptors.isEmpty()) {
                descriptors.append(',');
            }
            String[] parts = platform.split("/");
            descriptors.append("{\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",")
                    .append("\"digest\":\"").append(CHILD_DIGEST).append("\",")
                    .append("\"platform\":{\"os\":\"").append(parts[0])
                    .append("\",\"architecture\":\"").append(parts[1]).append("\"}}");
        }
        return "{\"mediaType\":\"application/vnd.oci.image.index.v1+json\",\"manifests\":["
                + descriptors + "]}";
    }

    private record Resolution(int status, String stdout, String stderr, String digest,
                              String requests, String ghArguments) {}

    private static final String FAKE_CURL = """
            #!/usr/bin/env bash
            set -euo pipefail
            method=GET
            headers=
            output=
            url=${!#}
            while [[ "$#" -gt 0 ]]; do
              case "$1" in
                --head) method=HEAD; shift ;;
                --dump-header) headers=$2; shift 2 ;;
                --output) output=$2; shift 2 ;;
                *) shift ;;
              esac
            done
            printf '%s %s\n' "$method" "$url" >> "$FAKE_REQUESTS"
            case "$url" in
              https://ghcr.io/v2/)
                printf 'WWW-Authenticate: Bearer realm="https://ghcr.io/token",service="ghcr.io"\n' > "$headers"
                printf '401' ;;
              https://ghcr.io/token)
                printf '{"token":"fixture-token"}' ;;
              */manifests/sha-*)
                status=${FAKE_ALIAS_STATUS:-200}
                if [[ "$status" == 200 ]]; then
                  printf 'Docker-Content-Digest: %s\n' "${FAKE_ALIAS_DIGEST:-$FAKE_DIGEST}" > "$headers"
                else
                  : > "$headers"
                fi
                printf '%s' "$status" ;;
              */manifests/sha256:*)
                printf 'Docker-Content-Digest: %s\n' "${FAKE_INDEX_HEADER_DIGEST:-$FAKE_DIGEST}" > "$headers"
                cp "$FAKE_INDEX_FILE" "$output"
                printf '200' ;;
              *) exit 1 ;;
            esac
            """;

    private static final String FAKE_GH = """
            #!/usr/bin/env bash
            printf '%s\n' "$@" > "$FAKE_GH_ARGS"
            printf 'human-readable verification details\n'
            exit "${FAKE_GH_STATUS:-0}"
            """;
}

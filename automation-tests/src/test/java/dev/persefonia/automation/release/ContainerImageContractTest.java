package dev.persefonia.automation.release;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import dev.persefonia.automation.support.InvocationLog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerImageContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final String source = "a".repeat(40);
    private final String sourceUrl = "https://github.com/example/persefonia";
    private final String image = "ghcr.io/example/persefonia";

    @Test
    void verifiesRawRegistryBodiesRuntimeConfigAndBothProvenanceAuthorities() throws Exception {
        Fixture fixture = fixture(source, true);
        CommandResult result = run(fixture, true);
        result.requireSuccess();
        assertThat(result.stdout()).contains("Registry top-level index digest verified", "BuildKit SPDX SBOM verified",
                "BuildKit SLSA v1 provenance verified", "GitHub signed provenance verified");
        List<List<String>> gh = InvocationLog.read(temporary.resolve("gh.log"));
        assertThat(gh).hasSize(1);
        assertThat(gh.getFirst()).contains("--bundle-from-oci", "--repo", "example/persefonia",
                "--predicate-type", "https://slsa.dev/provenance/v1", "--source-digest", source,
                "--source-ref", "refs/heads/master", "--signer-digest", "--signer-workflow",
                "example/persefonia/.github/workflows/delivery.yml", "--deny-self-hosted-runners");
    }

    @Test
    void rejectsCorruptRawIndexBeforeParsingOrSuccessfulOutput() throws Exception {
        Fixture fixture = fixture(source, true);
        Files.writeString(fixture.artifacts.resolve(fixture.indexDigest), "{\"tampered\":true}");
        CommandResult result = run(fixture, true);
        assertThat(result.status()).isNotZero();
        assertThat(result.stderr()).contains("does not match requested digest");
        assertThat(InvocationLog.read(temporary.resolve("gh.log"))).isEmpty();
    }

    @Test
    void rejectsWrongImageRevisionAndFailedGithubAttestation() throws Exception {
        Fixture wrongRevision = fixture("b".repeat(40), true);
        CommandResult mismatch = run(wrongRevision, true);
        assertThat(mismatch.status()).isNotZero();
        assertThat(mismatch.stderr()).contains("org.opencontainers.image.revision");
        assertThat(InvocationLog.read(temporary.resolve("gh.log"))).isEmpty();

        Fixture wrongProvenance = fixture(source, false);
        CommandResult provenance = run(wrongProvenance, true);
        assertThat(provenance.status()).isNotZero();
        assertThat(provenance.stderr()).contains("BuildKit provenance");
        assertThat(InvocationLog.read(temporary.resolve("gh.log"))).isEmpty();

        Fixture valid = fixture(source, true);
        CommandResult unsigned = run(valid, false);
        assertThat(unsigned.status()).isNotZero();
        assertThat(unsigned.stdout()).doesNotContain("GitHub signed provenance verified");
    }

    @Test
    void rejectsWrongSpdxPredicateAndDuplicateExpectedLayers() throws Exception {
        for (Fixture fixture : List.of(
                fixture(source, true, "https://spdx.dev/Document-EVIL", false, false),
                fixture(source, true, "https://spdx.dev/Document", true, false),
                fixture(source, true, "https://spdx.dev/Document", false, true))) {
            CommandResult result = run(fixture, true);
            assertThat(result.status()).isNotZero();
            assertThat(result.stdout()).doesNotContain("GitHub signed provenance verified");
            assertThat(InvocationLog.read(temporary.resolve("gh.log"))).isEmpty();
        }
    }

    private Fixture fixture(String revision, boolean validProvenance) throws Exception {
        return fixture(revision, validProvenance, "https://spdx.dev/Document", false, false);
    }

    private Fixture fixture(String revision, boolean validProvenance, String sbomType,
                            boolean duplicateSbom, boolean duplicateProvenance) throws Exception {
        Path artifacts = temporary.resolve("registry");
        Files.createDirectories(artifacts);
        String config = """
                {"config":{"Labels":{"org.opencontainers.image.title":"Persefonia","org.opencontainers.image.source":"%s","org.opencontainers.image.revision":"%s","org.opencontainers.image.version":"0.1.0"},"User":"10001:10001","WorkingDir":"/opt/persefonia","Cmd":["java","-jar","/opt/persefonia/persefonia.jar"]}}
                """.formatted(sourceUrl, revision).trim();
        String configDigest = store(artifacts, config);
        String childDigest = store(artifacts, "{\"config\":{\"digest\":\"" + configDigest + "\"}}");
        String sbomDigest = store(artifacts, "{\"predicateType\":\"" + sbomType
                + "\",\"predicate\":{\"spdxVersion\":\"SPDX-2.3\",\"packages\":[{\"name\":\"runtime\"}]}}");
        String buildType = validProvenance
                ? "https://github.com/moby/buildkit/blob/master/docs/attestations/slsa-definitions.md" : "wrong";
        String provenanceDigest = store(artifacts, "{\"predicateType\":\"https://slsa.dev/provenance/v1\",\"predicate\":{\"buildDefinition\":{\"buildType\":\"" + buildType + "\"}}}");
        String sbomLayer = """
                {"mediaType":"application/vnd.in-toto+json","annotations":{"in-toto.io/predicate-type":"https://spdx.dev/Document"},"digest":"%s"}
                """.formatted(sbomDigest).trim();
        String provenanceLayer = """
                {"mediaType":"application/vnd.in-toto+json","annotations":{"in-toto.io/predicate-type":"https://slsa.dev/provenance/v1"},"digest":"%s"}
                """.formatted(provenanceDigest).trim();
        List<String> layers = new java.util.ArrayList<>(List.of(sbomLayer, provenanceLayer));
        if (duplicateSbom) layers.add(sbomLayer);
        if (duplicateProvenance) layers.add(provenanceLayer);
        String attestation = "{\"layers\":[" + String.join(",", layers) + "]}";
        String attestationDigest = store(artifacts, attestation);
        String index = """
                {"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","platform":{"os":"linux","architecture":"amd64"}},{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","platform":{"os":"linux","architecture":"arm64"}},{"mediaType":"application/vnd.oci.image.manifest.v1+json","digest":"%s","platform":{"os":"unknown","architecture":"unknown"},"annotations":{"vnd.docker.reference.type":"attestation-manifest","vnd.docker.reference.digest":"%s"}}]}
                """.formatted(childDigest, childDigest, attestationDigest, childDigest).trim();
        String indexDigest = store(artifacts, index);
        Path indexFile = temporary.resolve("index.json");
        Files.writeString(indexFile, index);
        Path configDir = temporary.resolve("docker-config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.json"), "{\"auths\":{\"ghcr.io\":{\"auth\":\"dXNlcjpwYXNz\"}}}");
        return new Fixture(artifacts, indexFile, configDir, indexDigest);
    }

    private String store(Path directory, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String digest = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Files.write(directory.resolve(digest), bytes);
        return digest;
    }

    private CommandResult run(Fixture fixture, boolean signed) throws Exception {
        Path bin = fakes();
        Files.deleteIfExists(temporary.resolve("gh.log"));
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(30),
                Map.of("PATH", bin + ":/usr/bin:/bin", "DOCKER_CONFIG", fixture.configDir.toString(),
                        "FAKE_REGISTRY", fixture.artifacts.toString(), "FAKE_INDEX", fixture.indexFile.toString(),
                        "FAKE_GH_LOG", temporary.resolve("gh.log").toString(), "FAKE_GH_SIGNED", signed ? "1" : "0"))
                .run(repository.resolve("scripts/release/verify-container-image.sh").toString(),
                        image + "@" + fixture.indexDigest, "linux/amd64",
                        repository.resolve("docker/supported-platforms.txt").toString(), sourceUrl, source, "0.1.0");
    }

    private Path fakes() throws Exception {
        Path bin = temporary.resolve("bin");
        Files.createDirectories(bin);
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ $1 == buildx && $2 == version ]]; then exit 0; fi
                if [[ $1 == buildx && $2 == imagetools && $3 == inspect && $4 == --raw ]]; then
                  cat -- "$FAKE_INDEX"
                else
                  exit 90
                fi
                """);
        docker.toFile().setExecutable(true);
        Path gh = bin.resolve("gh");
        Files.writeString(gh, """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\0' "$#" "$@" >> "$FAKE_GH_LOG"
                [[ $FAKE_GH_SIGNED == 1 ]]
                """);
        gh.toFile().setExecutable(true);
        Path curl = bin.resolve("curl");
        Files.writeString(curl, """
                #!/usr/bin/env bash
                set -euo pipefail
                headers=
                body=
                url=
                authorized=0
                method=GET
                while (($#)); do
                  case $1 in
                    --dump-header) headers=$2; shift 2 ;;
                    --output) body=$2; shift 2 ;;
                    --header) if [[ $2 == Authorization:* ]]; then authorized=1; fi; shift 2 ;;
                    --write-out|--user|--data-urlencode|--max-time) shift 2 ;;
                    --head) method=HEAD; shift ;;
                    https://*) url=$1; shift ;;
                    *) shift ;;
                  esac
                done
                if [[ $url == https://ghcr.io/token ]]; then
                  printf '{"token":"synthetic"}'
                  exit 0
                fi
                digest=${url##*/}
                if [[ $authorized == 0 ]]; then
                  printf 'www-authenticate: Bearer realm="https://ghcr.io/token",service="ghcr.io",scope="repository:example/persefonia:pull"\\n' > "$headers"
                  : > "$body"
                  printf '401'
                elif [[ -f $FAKE_REGISTRY/$digest ]]; then
                  printf 'Docker-Content-Digest: %s\\n' "$digest" > "$headers"
                  if [[ $method == GET ]]; then cp -- "$FAKE_REGISTRY/$digest" "$body"; else : > "$body"; fi
                  printf '200'
                else
                  : > "$headers"
                  : > "$body"
                  printf '404'
                fi
                """);
        curl.toFile().setExecutable(true);
        return bin;
    }

    private record Fixture(Path artifacts, Path indexFile, Path configDir, String indexDigest) {}
}

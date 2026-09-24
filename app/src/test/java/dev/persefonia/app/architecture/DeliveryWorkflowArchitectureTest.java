package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeliveryWorkflowArchitectureTest {
    private static final Path DELIVERY_WORKFLOW = Path.of("../.github/workflows/delivery.yml");
    private static final Path SUPPORTED_PLATFORMS = Path.of("../docker/supported-platforms.txt");
    private static final Path BOOTJAR_VERIFIER = Path.of("../scripts/ci/verify-bootjar.sh");
    private static final Path IMAGE_VERIFIER = Path.of("../scripts/release/verify-container-image.sh");
    private static final Path ALIAS_PUBLISHER = Path.of("../scripts/release/publish-source-alias.sh");
    private static final Path DELIVERY_SUMMARY = Path.of("../scripts/release/write-delivery-summary.sh");
    private static final Path TOOLCHAIN_VERIFIER = Path.of("../scripts/release/verify-delivery-toolchain.sh");

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

    @Test
    void deliveryRejectsStaleWorkflowRunsAndSerializesTheSameSource() throws Exception {
        String workflow = Files.readString(DELIVERY_WORKFLOW);

        assertThat(workflow)
                .contains("github.event.workflow_run.conclusion == 'success'")
                .contains("github.event.workflow_run.event == 'push'")
                .contains("github.event.workflow_run.head_branch == 'master'")
                .contains("github.event.workflow_run.head_repository.full_name == github.repository")
                .contains("github.event.workflow_run.head_repository.fork == false")
                .contains("github.sha == github.event.workflow_run.head_sha")
                .contains("group: delivery-${{ github.event.workflow_run.head_sha }}")
                .contains("cancel-in-progress: false");
    }

    @Test
    void deliveryLocksAndChecksEveryBuildxBuilder() throws Exception {
        String workflow = Files.readString(DELIVERY_WORKFLOW);

        assertThat(workflow)
                .contains("BUILDX_VERSION: v0.37.1")
                .contains("BUILDKIT_VERSION: v0.33.0")
                .containsPattern("BUILDKIT_IMAGE: moby/buildkit@sha256:[a-f0-9]{64}")
                .doesNotContain("moby/buildkit:latest", "moby/buildkit:v0.33.0", "version: latest")
                .contains("BUILDER_NODES: ${{ steps.buildx.outputs.nodes }}");
        assertThat(workflow.split("uses: docker/setup-buildx-action@", -1)).hasSize(4);
        assertThat(workflow.split(Pattern.quote("version: ${{ env.BUILDX_VERSION }}"), -1)).hasSize(4);
        assertThat(workflow.split(Pattern.quote("image=${{ env.BUILDKIT_IMAGE }}"), -1)).hasSize(4);
        assertThat(workflow.split("id: buildx", -1)).hasSize(4);
        assertThat(workflow.split(Pattern.quote("BUILDER_NODES: ${{ steps.buildx.outputs.nodes }}"), -1)).hasSize(4);
        assertThat(workflow.split("scripts/release/verify-delivery-toolchain.sh", -1)).hasSize(4);
        assertThat(workflow).doesNotContain("jq -e --arg version \"$BUILDKIT_VERSION\"");
        for (String job : List.of("publish-candidate", "verify-candidate", "publish-source-alias")) {
            int start = workflow.indexOf("\n  " + job + ":");
            assertThat(start).isPositive();
            int end = workflow.length();
            for (String otherJob : List.of("publish-candidate", "verify-candidate", "publish-source-alias")) {
                int position = workflow.indexOf("\n  " + otherJob + ":", start + 1);
                if (position >= 0 && position < end) {
                    end = position;
                }
            }
            String section = workflow.substring(start, end);
            assertThat(section)
                    .contains("id: buildx")
                    .contains("version: ${{ env.BUILDX_VERSION }}")
                    .contains("image=${{ env.BUILDKIT_IMAGE }}")
                    .contains("BUILDER_NODES: ${{ steps.buildx.outputs.nodes }}")
                    .contains("./scripts/release/verify-delivery-toolchain.sh \"$BUILDX_VERSION\" \"$BUILDKIT_VERSION\" \"$BUILDER_NODES\"");
        }
    }

    @Test
    void toolchainVerifierChecksEveryEffectiveBuilderNode() throws Exception {
        String verifier = Files.readString(TOOLCHAIN_VERIFIER);

        assertThat(verifier)
                .contains("docker buildx version")
                .contains("actual_buildx")
                .contains("\"$actual_buildx\" != \"$expected_buildx\"")
                .contains("type == \"array\" and length > 0 and all(.[]; .buildkit == $version)")
                .doesNotContain("v0.37.1", "v0.33.0", ".[0].buildkit");
    }

    @Test
    void registryDigestChecksReplaceTheInvalidBuildxTemplate() throws Exception {
        String verifier = Files.readString(IMAGE_VERIFIER);
        String aliasPublisher = Files.readString(ALIAS_PUBLISHER);

        assertThat(verifier)
                .doesNotContain("{{.Digest}}")
                .contains("registry_request HEAD")
                .contains("docker-content-digest:")
                .contains("registry_head \"manifests/${index_digest}\"")
                .contains("\"$registry_digest\" != \"$index_digest\"");
        assertThat(aliasPublisher)
                .doesNotContain("{{.Digest}}")
                .contains("--head")
                .contains("docker-content-digest:")
                .contains("registry_head \"manifests/${image_digest}\"")
                .contains("registry_head \"manifests/${alias_tag}\"")
                .contains("404) ;;")
                .contains("\"$registry_digest\" != \"$image_digest\"");
    }

    @Test
    void aliasAbsenceRequiresEstablishedPullAuthorizationAndCoversEveryImageManifestType() throws Exception {
        String publisher = Files.readString(ALIAS_PUBLISHER);

        assertThat(publisher)
                .contains("registry_scope=\"repository:${repository}:pull\"")
                .contains("--user \"$registry_credentials\"")
                .contains("--header \"Authorization: Bearer $registry_token\"")
                .contains("application/vnd.oci.image.index.v1+json")
                .contains("application/vnd.docker.distribution.manifest.list.v2+json")
                .contains("application/vnd.oci.image.manifest.v1+json")
                .contains("application/vnd.docker.distribution.manifest.v2+json")
                .contains("registry_head \"manifests/${alias_tag}\" \"$alias_accept\"")
                .contains("404) ;;")
                .doesNotContain("registry_authenticated");
        assertThat(publisher.indexOf("registry_token=$(jq"))
                .isLessThan(publisher.indexOf("registry_head \"manifests/${image_digest}\""));
    }

    @Test
    void digestAddressedRegistryBodiesAreHashedBeforeParsing() throws Exception {
        String verifier = Files.readString(IMAGE_VERIFIER);

        assertThat(verifier)
                .contains("requested_digest=${path##*/}")
                .contains("verify_sha256_body \"$requested_digest\" \"$registry_body\"")
                .contains("sha256sum -- \"$body_file\"")
                .contains("\"sha256:$actual\" != \"$expected\"")
                .contains("\"$registry_digest\" != \"$requested_digest\"");
        assertThat(verifier.indexOf("verify_sha256_body \"$requested_digest\""))
                .isLessThan(verifier.indexOf("cat \"$registry_body\""));
    }

    @Test
    void deliverySummaryIsRenderedByARepositoryOwnedPresentationScript() throws Exception {
        String workflow = Files.readString(DELIVERY_WORKFLOW);
        String summary = Files.readString(DELIVERY_SUMMARY);

        assertThat(workflow)
                .contains("run: ./scripts/release/write-delivery-summary.sh \"$GITHUB_STEP_SUMMARY\"")
                .doesNotContain("DELIVERY_AMD64_CHILD_DIGEST", "DELIVERY_ARM64_CHILD_DIGEST")
                .doesNotContain("id: alias", "cat >> \"$GITHUB_STEP_SUMMARY\"", "manifest=$(docker buildx imagetools inspect --raw");
        assertThat(summary)
                .contains("## Delivery", "Top-level OCI index digest", "BuildKit version")
                .doesNotContain("curl ", "docker buildx imagetools", "gh attestation")
                .doesNotContain("DELIVERY_AMD64_CHILD_DIGEST", "DELIVERY_ARM64_CHILD_DIGEST");
        assertThat(Files.readString(ALIAS_PUBLISHER))
                .contains("docker buildx imagetools create")
                .doesNotContain("GITHUB_OUTPUT", "write_child_digest_outputs", "imagetools inspect --raw");
    }

    @Test
    void signedProvenanceMustComeFromTheRegistryWithExactSourceAndSigner() throws Exception {
        String verifier = Files.readString(IMAGE_VERIFIER);
        String workflow = Files.readString(DELIVERY_WORKFLOW);

        assertThat(verifier)
                .contains("--bundle-from-oci", "--repo \"$repository_slug\"")
                .contains("--predicate-type https://slsa.dev/provenance/v1")
                .contains("--source-digest \"$expected_source_sha\"")
                .contains("--source-ref refs/heads/master")
                .contains("--signer-digest \"$expected_source_sha\"")
                .contains("--signer-workflow \"$signer_workflow\"")
                .contains("--deny-self-hosted-runners")
                .contains("$repository_slug/.github/workflows/delivery.yml");
        assertThat(workflow).doesNotContain("attestations: read", "GH_TOKEN: ${{ github.token }}");
    }
}

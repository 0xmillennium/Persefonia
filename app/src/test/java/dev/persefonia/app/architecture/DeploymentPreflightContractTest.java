package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentPreflightContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsEveryInheritedDockerAndComposeOverrideEvenWhenEmptyBeforeInvokingDocker() throws Exception {
        for (String key : List.of("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH",
                "COMPOSE_PROJECT_NAME", "COMPOSE_FILE")) {
            for (String value : List.of("", "unexpected-override")) {
                var result = PreflightTestSupport.run(temporaryDirectory, Map.of(key, value));

                assertThat(result.status()).as("%s=%s", key, value).isNotZero();
                assertThat(result.output()).contains(key + " must not be defined in the inherited process environment")
                        .doesNotContain(value.isEmpty() ? "required file" : value);
                assertThat(result.dockerCalls()).isEmpty();
            }
        }
    }

    @Test
    void absentForbiddenProcessVariablesAndCanonicalImageReachTheRuntimeFileGate() throws Exception {
        var result = PreflightTestSupport.run(temporaryDirectory, Map.of());

        assertRuntimeFileGate(result);
    }

    @Test
    void doesNotBanDockerConfigOrComposeProfilesOutsideTheLockedBoundary() throws Exception {
        var result = PreflightTestSupport.run(temporaryDirectory, Map.of(
                "DOCKER_CONFIG", temporaryDirectory.resolve("docker-config").toString(),
                "COMPOSE_PROFILES", "operator-profile"));

        assertRuntimeFileGate(result);
    }

    @Test
    void rejectsNonCanonicalRepositoriesTagsAndMalformedDigestsBeforeRuntimeValidation() throws Exception {
        String digest = "a".repeat(64);
        String image = PreflightTestSupport.IMAGE_REFERENCE;
        List<String> invalid = List.of(
                "busybox@sha256:" + digest,
                "docker.io/library/busybox@sha256:" + digest,
                "ghcr.io/0xmillennium/other@sha256:" + digest,
                "ghcr.io/other/persefonia@sha256:" + digest,
                "ghcr.io/0xmillennium/Persefonia@sha256:" + digest,
                "ghcr.io/0xmillennium/persefonia:latest",
                "ghcr.io/0xmillennium/persefonia:v1@sha256:" + digest,
                image.replace("sha256:", "sha512:"),
                image.substring(0, image.length() - 1), image + "a",
                image.replace(digest, digest.toUpperCase(java.util.Locale.ROOT)),
                " " + image, image + " ", "\t" + image, image + "\r",
                image + "\n", "\n" + image, image + "\ninjected", "injected\n" + image,
                image.substring(0, image.length() - 1) + "\n");
        for (String reference : invalid) {
            var result = PreflightTestSupport.run(temporaryDirectory, Map.of("PERSEFONIA_IMAGE_REF", reference));

            assertThat(result.status()).as(reference).isNotZero();
            assertThat(result.output()).contains(
                    "PERSEFONIA_IMAGE_REF must be exactly ghcr.io/0xmillennium/persefonia@sha256:<64 lowercase hex>")
                    .doesNotContain("required file", "configuration validation failed");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    @Test
    void requiresImageReferenceFromTheDeploymentInvocation() throws Exception {
        var result = PreflightTestSupport.run(temporaryDirectory, Map.of("PERSEFONIA_IMAGE_REF", ""));

        assertThat(result.status()).isNotZero();
        assertThat(result.output()).contains("PERSEFONIA_IMAGE_REF must be supplied by the deployment invocation");
        assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
    }

    @Test
    void envFileOverridesRemainRejectedIndependentlyOfTheProcessEnvironment() throws Exception {
        for (String key : List.of("PERSEFONIA_IMAGE_REF", "COMPOSE_PROJECT_NAME", "COMPOSE_FILE",
                "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH",
                "POSTGRES_PASSWORD", "REDIS_PASSWORD", "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET",
                "PERSEFONIA_OIDC_CLIENT_SECRET", "PERSEFONIA_CLOUDFLARE_API_TOKEN")) {
            Path env = Files.createTempFile(temporaryDirectory, "forbidden-", ".env");
            Files.writeString(env, key + "=\n");
            var result = PreflightTestSupport.run(temporaryDirectory, Map.of("PERSEFONIA_ENV_FILE", env.toString()));

            assertThat(result.status()).as(key).isNotZero();
            assertThat(result.output()).contains(key + " must not be defined in " + env)
                    .doesNotContain("inherited process environment", "required file");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
    }

    private static void assertRuntimeFileGate(PreflightTestSupport.Result result) {
        // Missing synthetic env stops execution before host secrets, networks, media, or Compose rendering.
        assertThat(result.status()).isNotZero();
        assertThat(result.output()).contains("required file is missing or unreadable:", "missing.env")
                .doesNotContain("inherited process environment", "PERSEFONIA_IMAGE_REF must");
        assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
    }
}

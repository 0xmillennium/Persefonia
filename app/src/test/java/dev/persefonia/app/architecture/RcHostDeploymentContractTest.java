package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RcHostDeploymentContractTest {
    private static final Path SCRIPT = Path.of("../scripts/deploy/rc-host-deploy.sh").toAbsolutePath();
    private static final Path FIXTURES = Path.of("src/test/resources/architecture/rc-deployment");
    private static final String SOURCE = "a".repeat(40);
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia@sha256:" + "b".repeat(64);
    private static final String POSTGRES = "0".repeat(63) + "1";
    private static final String REDIS = "0".repeat(63) + "2";
    private static final String APP = "0".repeat(63) + "3";
    private static final String IMAGE_ID = "sha256:" + "0".repeat(63) + "4";
    private static final String PROTOCOL = "format_version=1\nsource_sha=" + SOURCE + "\nimage_reference=" + IMAGE
            + "\npostgres_container_id=" + POSTGRES + "\nredis_container_id=" + REDIS
            + "\napp_container_id=" + APP + "\napp_image_id=" + IMAGE_ID + "\napp_health=healthy\n";
    @TempDir Path temp;

    @Test
    void invokesOnlyTheFixedGatewayInExactOrderAndEmitsTheExistingProtocol() throws Exception {
        Result result = run(Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(PROTOCOL);
        assertThat(result.calls()).containsExactly(
                "preflight " + IMAGE,
                "pull-app " + IMAGE,
                "service-id " + IMAGE + " postgres",
                "service-id " + IMAGE + " redis",
                "up-app " + IMAGE,
                "service-id " + IMAGE + " postgres",
                "service-id " + IMAGE + " redis",
                "service-id " + IMAGE + " app",
                "app-health " + IMAGE + " " + APP,
                "app-config-image " + IMAGE + " " + APP,
                "app-image-id " + IMAGE + " " + APP,
                "qualified-image-id " + IMAGE);
        assertThat(result.sudoCalls()).hasSize(12).allMatch(call -> call.startsWith(
                "sudo <-n> <--> </usr/local/libexec/persefonia-runtimectl>"));
    }

    @Test
    void acceptsAnIdempotentSameDigestRerunWithoutRequiringAppRecreation() throws Exception {
        Result first = run(Map.of());
        Result retry = run(Map.of());
        assertThat(first.status()).isZero();
        assertThat(retry.status()).isZero();
        assertThat(retry.stdout()).isEqualTo(first.stdout());
        assertThat(retry.calls().stream().filter(call -> call.startsWith("service-id " + IMAGE + " app")
                || call.startsWith("app-health ") || call.startsWith("app-config-image ")
                || call.startsWith("app-image-id "))).containsExactly(
                "service-id " + IMAGE + " app",
                "app-health " + IMAGE + " " + APP,
                "app-config-image " + IMAGE + " " + APP,
                "app-image-id " + IMAGE + " " + APP);
    }

    @Test
    void rejectsInvalidInputsAndUnavailableSudoBeforeGatewayInvocation() throws Exception {
        for (Map<String, String> overrides : List.of(
                Map.of("SOURCE", "bad"), Map.of("SOURCE", SOURCE.toUpperCase()),
                Map.of("IMAGE", "ghcr.io/0xmillennium/persefonia:latest"),
                Map.of("IMAGE", "ghcr.io/0xmillennium/persefonia:tag@sha256:" + "b".repeat(64)),
                Map.of("IMAGE", "ghcr.io/other/persefonia@sha256:" + "b".repeat(64)),
                Map.of("IMAGE", IMAGE.toUpperCase()), Map.of("IMAGE", IMAGE + "\n"),
                Map.of("NO_SUDO", "1"))) {
            Result result = run(overrides);
            assertThat(result.status()).as(overrides.toString()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.calls()).isEmpty();
            assertThat(result.sudoCalls()).isEmpty();
        }
    }

    @Test
    void failsClosedAtEveryRuntimeOperation() throws Exception {
        for (String failure : List.of("preflight", "pull-app", "service-id-postgres-before",
                "service-id-redis-before", "up-app", "service-id-postgres-after",
                "service-id-redis-after", "service-id-app-after", "app-health",
                "app-config-image", "app-image-id", "qualified-image-id")) {
            Result result = run(Map.of("RC_FAIL_OP", failure));
            assertFailed(result, failure);
            assertThat(result.stderr()).as(failure).contains("synthetic " + failure + " failure");
            if (failure.equals("preflight")) assertThat(result.calls()).containsExactly("preflight " + IMAGE);
            if (failure.equals("pull-app")) assertThat(result.calls()).containsExactly("preflight " + IMAGE, "pull-app " + IMAGE);
            if (failure.equals("service-id-postgres-before") || failure.equals("service-id-redis-before")) {
                assertThat(result.calls()).noneMatch(call -> call.startsWith("up-app "));
            }
        }
    }

    @Test
    void rejectsMissingMalformedAndMultilineServiceIdentitiesBeforeUpdate() throws Exception {
        for (String key : List.of("RC_POSTGRES_BEFORE", "RC_REDIS_BEFORE")) {
            for (String value : List.of("", "not-an-id", POSTGRES + "\n" + POSTGRES, POSTGRES + "\n")) {
                Result result = run(Map.of(key, value));
                assertFailed(result, key + "=" + value);
                assertThat(result.calls()).noneMatch(call -> call.startsWith("up-app "));
            }
        }
        for (String value : List.of("", "invalid", APP + "\n" + APP, APP + "\n")) {
            assertFailed(run(Map.of("RC_APP_ID", value)), "app=" + value);
        }
    }

    @Test
    void rejectsChangedDependenciesAndEveryInvalidApplicationPostcondition() throws Exception {
        for (Map<String, String> overrides : List.of(
                Map.of("RC_POSTGRES_AFTER", "9".repeat(64)),
                Map.of("RC_REDIS_AFTER", "9".repeat(64)),
                Map.of("RC_POSTGRES_AFTER", "malformed"),
                Map.of("RC_REDIS_AFTER", REDIS + "\n" + REDIS),
                Map.of("RC_HEALTH", ""), Map.of("RC_HEALTH", "starting"),
                Map.of("RC_HEALTH", "unhealthy"), Map.of("RC_HEALTH", "healthy\nhealthy"),
                Map.of("RC_CONFIG_IMAGE", "bad"),
                Map.of("RC_CONFIG_IMAGE", "ghcr.io/0xmillennium/persefonia@sha256:" + "c".repeat(64)),
                Map.of("RC_CONFIG_IMAGE", IMAGE + "\n" + IMAGE),
                Map.of("RC_APP_IMAGE_ID", "bad"),
                Map.of("RC_APP_IMAGE_ID", IMAGE_ID + "\n" + IMAGE_ID),
                Map.of("RC_QUALIFIED_IMAGE_ID", "bad"),
                Map.of("RC_QUALIFIED_IMAGE_ID", IMAGE_ID + "\n" + IMAGE_ID),
                Map.of("RC_NUL_OP", "app-health"),
                Map.of("RC_APP_IMAGE_ID", "sha256:" + "9".repeat(64)))) {
            assertFailed(run(overrides), overrides.toString());
        }
    }

    private static void assertFailed(Result result, String description) {
        assertThat(result.status()).as(description).isNotZero();
        assertThat(result.stdout()).as(description).isEmpty();
        assertThat(result.calls()).as(description).noneMatch(call -> call.startsWith("rollback "));
    }

    private Result run(Map<String, String> overrides) throws Exception {
        Path dir = Files.createTempDirectory(temp, "gateway-");
        Path bin = Files.createDirectory(dir.resolve("bin"));
        Path gateway = dir.resolve("gateway");
        Files.copy(FIXTURES.resolve("fake-rc-runtime-gateway.sh"), gateway);
        gateway.toFile().setExecutable(true);
        if (overrides.containsKey("NO_SUDO")) {
            Files.createSymbolicLink(bin.resolve("bash"), Path.of("/usr/bin/bash"));
        } else {
            Path sudo = bin.resolve("sudo");
            Files.copy(FIXTURES.resolve("fake-rc-sudo.sh"), sudo);
            sudo.toFile().setExecutable(true);
        }
        Path sudoLog = dir.resolve("sudo-calls"), gatewayLog = dir.resolve("gateway-calls");
        Path tmp = Files.createDirectory(dir.resolve("tmp"));
        ProcessBuilder builder = new ProcessBuilder(SCRIPT.toString(), overrides.getOrDefault("SOURCE", SOURCE),
                overrides.getOrDefault("IMAGE", IMAGE));
        builder.environment().put("PATH", overrides.containsKey("NO_SUDO") ? bin.toString()
                : bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("RC_SUDO_LOG", sudoLog.toString());
        builder.environment().put("RC_GATEWAY_LOG", gatewayLog.toString());
        builder.environment().put("RC_FAKE_GATEWAY", gateway.toString());
        builder.environment().put("RC_IMAGE_REF", IMAGE);
        builder.environment().put("TMPDIR", tmp.toString());
        builder.environment().putAll(overrides);
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int status = process.waitFor();
        try (var paths = Files.list(tmp)) {
            assertThat(paths.toList()).as("gateway output files must be removed").isEmpty();
        }
        return new Result(status, stdout, stderr, lines(gatewayLog), lines(sudoLog));
    }

    private static List<String> lines(Path file) throws Exception {
        return Files.exists(file) ? Files.readAllLines(file) : new ArrayList<>();
    }

    private record Result(int status, String stdout, String stderr, List<String> calls, List<String> sudoCalls) {}
}

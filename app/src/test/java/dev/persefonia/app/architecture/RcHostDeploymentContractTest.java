package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RcHostDeploymentContractTest {
    private static final Path SCRIPT = Path.of("../scripts/deploy/rc-host-deploy.sh").toAbsolutePath();
    private static final Path FIXTURES = Path.of("src/test/resources/architecture/rc-deployment");
    private static final String SOURCE = "a".repeat(40);
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia@sha256:" + "b".repeat(64);
    @TempDir Path temp;

    @Test
    void deploysOnlyAppAfterPreflightAndVerifiesExternalContainerIdentities() throws Exception {
        Result result = run(Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo("format_version=1\nsource_sha=" + SOURCE + "\nimage_reference=" + IMAGE
                + "\npostgres_container_id=" + "0".repeat(63) + "1\nredis_container_id=" + "0".repeat(63) + "2"
                + "\napp_container_id=" + "0".repeat(63) + "3\napp_image_id=sha256:" + "0".repeat(63) + "4"
                + "\napp_health=healthy\n");
        List<String> calls = result.calls().lines().toList();
        assertThat(calls).hasSize(12);
        assertThat(calls.get(0)).startsWith("preflight " + IMAGE + " ");
        assertThat(calls.get(1)).contains("<pull> <app>");
        assertThat(calls.get(2)).contains("<ps> <-q> <postgres>");
        assertThat(calls.get(3)).contains("<ps> <-q> <redis>");
        assertThat(calls.get(4)).contains("<up> <--detach> <--no-deps> <--no-build> <--pull> <never> <--wait> <--wait-timeout> <180> <app>");
        assertThat(calls.get(5)).contains("<ps> <-q> <postgres>");
        assertThat(calls.get(6)).contains("<ps> <-q> <redis>");
        assertThat(calls.get(7)).contains("<ps> <-q> <app>");
        for (int i = 1; i <= 7; i++) {
            assertThat(calls.get(i)).contains("<compose> <--env-file> <", "> <-f> <", "ENV:1:" + IMAGE);
        }
        assertThat(result.calls()).doesNotContain("<down>", "<restart>", "<postgres> <up>", "<redis> <up>");
    }

    @Test
    void failsBeforeAnyDockerOnInvalidInputsOrFailedPreflight() throws Exception {
        for (Map<String, String> overrides : List.of(
                Map.of("SOURCE", "bad"), Map.of("IMAGE", "latest"),
                Map.of("MISSING_STACK", "1"),
                Map.of("MISSING", "docker-compose.yml"), Map.of("MISSING", ".env"),
                Map.of("MISSING", "scripts/preflight.sh"), Map.of("RC_PREFLIGHT_STATUS", "1"))) {
            Result result = run(overrides);
            assertThat(result.status()).as(overrides.toString()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.calls()).doesNotContain("docker");
        }
    }

    @Test
    void failsClosedOnPullDependencyUpdateOrRuntimeIdentityFailure() throws Exception {
        for (String failure : List.of("pull", "postgres_missing", "redis_missing", "up",
                "postgres_changed", "redis_changed", "app_missing", "app_multiple", "health", "config", "image")) {
            Result result = run(Map.of("RC_FAIL", failure));
            assertThat(result.status()).as(failure).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.calls()).doesNotContain("<down>", "<restart>", "<build>");
            if (failure.equals("pull") || failure.equals("postgres_missing") || failure.equals("redis_missing")) {
                assertThat(result.calls()).doesNotContain("<up>");
            }
        }
    }

    private Result run(Map<String, String> overrides) throws Exception {
        Path home = Files.createTempDirectory(temp, "home-");
        Path stack = home.resolve("Projects/Homie-Lab/extensions/Website-Stack");
        Files.createDirectories(stack.resolve("scripts"));
        Files.writeString(stack.resolve("docker-compose.yml"), "synthetic interface\n");
        Files.writeString(stack.resolve(".env"), "synthetic interface\n");
        Path preflight = stack.resolve("scripts/preflight.sh");
        Files.copy(FIXTURES.resolve("fake-rc-preflight.sh"), preflight);
        preflight.toFile().setExecutable(true);
        Path bin = Files.createDirectory(home.resolve("bin"));
        Path docker = bin.resolve("docker");
        Files.copy(FIXTURES.resolve("fake-rc-docker.sh"), docker);
        docker.toFile().setExecutable(true);
        String missing = overrides.get("MISSING");
        if (missing != null) Files.delete(stack.resolve(missing));
        if (overrides.containsKey("MISSING_STACK")) {
            Files.delete(stack.resolve("scripts/preflight.sh"));
            Files.delete(stack.resolve("scripts"));
            Files.delete(stack.resolve("docker-compose.yml"));
            Files.delete(stack.resolve(".env"));
            Files.delete(stack);
        }
        Path log = home.resolve("calls");
        ProcessBuilder builder = new ProcessBuilder(SCRIPT.toString(), overrides.getOrDefault("SOURCE", SOURCE),
                overrides.getOrDefault("IMAGE", IMAGE));
        builder.environment().put("HOME", home.toString());
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("RC_CALL_LOG", log.toString());
        builder.environment().put("RC_IMAGE_REF", IMAGE);
        builder.environment().put("RC_POSTGRES_MARK", home.resolve("postgres-mark").toString());
        builder.environment().put("RC_REDIS_MARK", home.resolve("redis-mark").toString());
        builder.environment().putAll(overrides);
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        process.getErrorStream().readAllBytes();
        return new Result(process.waitFor(), stdout, Files.exists(log) ? Files.readString(log) : "");
    }

    private record Result(int status, String stdout, String calls) {}
}

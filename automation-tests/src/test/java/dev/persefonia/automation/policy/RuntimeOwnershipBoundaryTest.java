package dev.persefonia.automation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeOwnershipBoundaryTest {
    @TempDir Path temporary;
    @Test
    void repositoryDoesNotOwnProductionTopologyDependencyServersOrHostPreflight() {
        for (String removed : List.of("compose.production.yaml", ".env.production.example",
                "scripts/deploy/preflight.sh", "docker/postgresql", "docker/redis",
                "docker/redis-start.sh", "docker/redis-start-test.sh")) {
            assertThat(Path.of("../" + removed)).doesNotExist();
        }
    }

    @Test
    void trackedBuildAndDeploymentSourceDoesNotOwnHostRuntime() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        var result = new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of())
                .run("git", "ls-files");
        result.requireSuccess();
        assertThat(result.stdout().lines().toList()).isNotEmpty();
        for (String file : result.stdout().lines().toList()) {
            assertThat(prohibitedRuntimeArtifact(file)).as(file).isFalse();
            if (file.startsWith("scripts/deploy/") && file.endsWith(".sh")) {
                assertThat(prohibitedHostImplementation(Files.readString(repository.resolve(file)))).as(file).isFalse();
            }
        }
    }

    @Test
    void semanticOwnershipPolicyAllowsNestedBuildSupportAndRejectsHostArtifacts() {
        for (String file : List.of("docker/Dockerfile", "docker/supported-platforms.txt",
                "docker/build/assets/entrypoint.sh", "compose.yaml", ".env.example",
                "secrets/postgres_password.examples")) {
            assertThat(prohibitedRuntimeArtifact(file)).as(file).isFalse();
        }
        for (String file : List.of("docker/postgresql/postgresql.conf", "docker/redis/redis.conf",
                "docker/postgres/postgresql.conf", "docker/build/pg_hba.conf", "docker/build/redis.conf",
                "postgresql.conf", "redis.conf",
                "docker/compose.production.yml", "compose.production.yaml", ".env.production",
                "docker/runtime-gateway/run.sh",
                "docker/host/preflight.sh", "docker/build/.env.production",
                "docker/secrets/rc-key", "secrets/production_password", "runtime.key",
                "docker/redis-start.sh", "scripts/deploy/runtimectl",
                "scripts/deploy/preflight.sh", "scripts/deploy/host-preflight.sh")) {
            assertThat(prohibitedRuntimeArtifact(file)).as(file).isTrue();
        }
        for (String content : List.of("/home/operator/Website-Stack/docker-compose.yml",
                "runuser -u private_operator -- docker compose up", "sudo docker compose up")) {
            assertThat(prohibitedHostImplementation(content)).as(content).isTrue();
        }
    }

    private static boolean prohibitedRuntimeArtifact(String file) {
        String path = "/" + file.toLowerCase() + "/";
        String name = Path.of(file).getFileName().toString().toLowerCase();
        boolean restrictedSource = file.startsWith("docker/") || file.startsWith("scripts/deploy/");
        return (file.startsWith("secrets/") && !name.endsWith(".examples"))
                || path.matches(".*(/postgres(?:ql)?/|/redis/|/host/|/runtime-gateway/|/website-stack/|/homie-lab/).*")
                || (restrictedSource && path.matches(".*/secrets?/.*"))
                || name.matches(".*(?:compose.*(?:prod|production)|(?:prod|production).*compose).*\\.ya?ml")
                || (restrictedSource && name.matches("(?:\\.env(?:\\..*)?|.*\\.(?:pem|key)|redis-start.*|.*(?:runtime.?gateway|runtimectl|host.?preflight).*)"))
                || name.matches("(?:postgresql\\.conf|pg_hba\\.conf|redis\\.conf)")
                || name.matches(".*\\.(?:pem|key)")
                || name.matches("\\.env(?:\\.production(?:\\..*)?)?")
                || (file.startsWith("scripts/deploy/") && name.equals("preflight.sh"));
    }

    private static boolean prohibitedHostImplementation(String content) {
        return content.contains("Website-Stack") || content.contains("Homie-Lab")
                || content.contains("/home/") || content.contains("runuser")
                || content.contains("docker compose") || content.contains("sudo docker");
    }

    @Test
    void remotePayloadUsesOnlyTheNarrowExternalRuntimeGateway() throws Exception {
        String payload = Files.readString(Path.of("../scripts/deploy/rc-host-deploy.sh"));
        assertThat(payload).contains("sudo -n -- /usr/local/libexec/persefonia-runtimectl \"$@\"");
        assertThat(prohibitedHostImplementation(payload)).isFalse();
        assertThat(payload).doesNotContain("docker-compose.yml",
                "PERSEFONIA_ENV_FILE", "scripts/preflight.sh", "/home/", "runuser",
                "docker compose", "docker inspect", "docker image inspect", "sudo docker",
                "sudo bash", "sudo sh", "sudo -u", "sudo -S", "\"$HOME\"");
    }
}

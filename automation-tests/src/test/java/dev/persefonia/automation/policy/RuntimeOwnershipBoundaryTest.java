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
    void dockerSourceDoesNotOwnNestedHostRuntimeTopology() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        var result = new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of())
                .run("git", "ls-files", "--", "docker/");
        result.requireSuccess();
        assertThat(result.stdout().lines().toList()).isNotEmpty();
        for (String file : result.stdout().lines().toList()) {
            assertThat(Path.of(file).getNameCount()).as(file).isEqualTo(2);
            assertThat(file).as(file).doesNotContain("postgres", "redis", "compose", "gateway", "preflight");
        }
    }

    @Test
    void remotePayloadUsesOnlyTheNarrowExternalRuntimeGateway() throws Exception {
        String payload = Files.readString(Path.of("../scripts/deploy/rc-host-deploy.sh"));
        assertThat(payload).contains("sudo -n -- /usr/local/libexec/persefonia-runtimectl \"$@\"");
        assertThat(payload).doesNotContain("docker-compose.yml",
                "PERSEFONIA_ENV_FILE", "scripts/preflight.sh", "/home/", "runuser",
                "docker compose", "docker inspect", "docker image inspect", "sudo docker",
                "sudo bash", "sudo sh", "sudo -u", "sudo -S", "\"$HOME\"");
    }
}

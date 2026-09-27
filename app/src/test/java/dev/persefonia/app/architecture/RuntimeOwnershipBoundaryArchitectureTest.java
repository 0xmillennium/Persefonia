package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RuntimeOwnershipBoundaryArchitectureTest {
    @Test
    void repositoryDoesNotOwnProductionTopologyDependencyServersOrHostPreflight() {
        for (String removed : List.of("compose.production.yaml", ".env.production.example",
                "scripts/deploy/preflight.sh", "docker/postgresql", "docker/redis",
                "docker/redis-start.sh", "docker/redis-start-test.sh")) {
            assertThat(Path.of("../" + removed)).doesNotExist();
        }
    }

    @Test
    void dockerSourceContainsOnlyApplicationImageInputs() throws Exception {
        List<String> expected = List.of("docker/Dockerfile", "docker/supported-platforms.txt");
        try (Stream<Path> paths = Files.walk(Path.of("../docker"))) {
            assertThat(paths.filter(Files::isRegularFile)
                    .map(path -> Path.of("..").relativize(path).toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
        Process process = new ProcessBuilder("git", "-C", "..", "ls-files", "--", "docker/")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero();
        // Unstaged deletions remain in the index until human review/staging.
        assertThat(output.lines().filter(file -> Files.exists(Path.of("../" + file))).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}

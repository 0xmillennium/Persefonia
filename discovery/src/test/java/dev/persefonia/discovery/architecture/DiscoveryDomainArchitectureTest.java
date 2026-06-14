package dev.persefonia.discovery.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiscoveryDomainArchitectureTest {
    @Test
    void discoveryDomainHasNoFrameworkInfrastructureOrForeignContextImports() throws IOException {
        assertThat(sourceText(projectRoot().resolve("discovery/src/main/java/dev/persefonia/discovery/domain")))
                .doesNotContain(
                        "org.springframework",
                        "java.sql",
                        "javax.sql",
                        "jakarta.persistence",
                        "javax.persistence",
                        "org.hibernate",
                        "jakarta.servlet",
                        "javax.servlet",
                        "dev.persefonia.app",
                        "dev.persefonia.contentpublishing",
                        "dev.persefonia.webpublic",
                        "dev.persefonia.webadmin");
    }

    @Test
    void sourceAndWebContextsDoNotDependOnOrConstructDiscoveryDomain() throws IOException {
        Path root = projectRoot();
        String sourceContextText = sourceText(
                root.resolve("content-publishing/src/main/java"),
                root.resolve("profile-portfolio/src/main/java"),
                root.resolve("taxonomy/src/main/java"),
                root.resolve("media-library/src/main/java"),
                root.resolve("web-public/src/main/java"),
                root.resolve("app/src/main/java"));

        assertThat(sourceContextText)
                .doesNotContain("dev.persefonia.discovery.domain", "new DiscoverableResource");
    }

    private static Path projectRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return workingDirectory.getFileName().toString().equals("discovery")
                ? workingDirectory.getParent()
                : workingDirectory;
    }

    private static String sourceText(Path... directories) throws IOException {
        StringBuilder source = new StringBuilder();
        for (Path directory : directories) {
            if (!Files.exists(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> source.append(readString(path)).append('\n'));
            }
        }
        return source.toString();
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source boundary", exception);
        }
    }
}

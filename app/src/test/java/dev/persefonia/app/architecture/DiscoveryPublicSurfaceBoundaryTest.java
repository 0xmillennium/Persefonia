package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DiscoveryPublicSurfaceBoundaryTest {
    private static final Pattern DISCOVERABLE_RESOURCE_CONSTRUCTION = Pattern.compile(
            "\\bnew\\s+DiscoverableResource\\s*\\(|\\bDiscoverableResource\\.builder\\s*\\(|\\bDiscoverableResource\\.create\\w*\\s*\\(");

    @Test
    void webPublicDoesNotCallRepositoriesJdbcOrSourcePersistenceForPublicSurfaces() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..persistence..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .orShould().dependOnClassesThat().haveSimpleName("JdbcTemplate")
                .orShould().dependOnClassesThat().haveSimpleName("NamedParameterJdbcTemplate")
                .orShould().dependOnClassesThat().haveSimpleName("ContentItemRepository")
                .orShould().dependOnClassesThat().haveSimpleName("ProjectRepository")
                .orShould().dependOnClassesThat().haveSimpleName("TagRepository")
                .orShould().dependOnClassesThat().haveSimpleName("SeriesRepository")
                .orShould().dependOnClassesThat().haveSimpleName("AssetRepository")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void sourceContextsDoNotConstructDiscoveryAggregatesDirectly() throws Exception {
        List<Path> offendingSources = productionSources(List.of(
                        Path.of("../content-publishing/src/main/java"),
                        Path.of("../profile-portfolio/src/main/java"),
                        Path.of("../taxonomy/src/main/java")))
                .filter(DiscoveryPublicSurfaceBoundaryTest::constructsDiscoverableResource)
                .toList();

        assertThat(offendingSources).isEmpty();
    }

    @Test
    void webPublicDoesNotQuerySourceRepositoriesByName() throws Exception {
        String webPublic = joinedSources(Path.of("../web-public/src/main/java"));

        assertThat(webPublic)
                .doesNotContain("ContentItemRepository")
                .doesNotContain("ProjectRepository")
                .doesNotContain("TagRepository")
                .doesNotContain("SeriesRepository")
                .doesNotContain("AssetRepository")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("NamedParameterJdbcTemplate");
    }

    private static Stream<Path> productionSources(List<Path> roots) {
        return roots.stream()
                .filter(Files::exists)
                .flatMap(root -> {
                    try {
                        return Files.walk(root);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not walk " + root, exception);
                    }
                })
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static boolean constructsDiscoverableResource(Path source) {
        try {
            return DISCOVERABLE_RESOURCE_CONSTRUCTION.matcher(Files.readString(source)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }

    private static String joinedSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(DiscoveryPublicSurfaceBoundaryTest::read)
                    .reduce("", String::concat);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}

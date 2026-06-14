package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContentPublishingDiscoveryBoundaryArchitectureTest {
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Pattern DISCOVERABLE_RESOURCE_CONSTRUCTION =
            Pattern.compile("\\bnew\\s+DiscoverableResource\\s*\\(");
    private static final Pattern PRODUCTION_NOOP_COORDINATOR =
            Pattern.compile("\\bContentDiscoverabilityCoordinator\\s+noop\\s*\\(");

    @Test
    void contentPublishingOnlyImportsDiscoveryApplicationPortsContractsAndCommands() throws IOException {
        List<String> forbiddenImports = javaSources()
                .flatMap(ContentPublishingDiscoveryBoundaryArchitectureTest::discoveryImports)
                .filter(ContentPublishingDiscoveryBoundaryArchitectureTest::isForbiddenDiscoveryImport)
                .toList();

        assertThat(forbiddenImports).isEmpty();
    }

    @Test
    void contentPublishingDoesNotConstructDiscoveryAggregatesOrReferenceDiscoveryTables() throws IOException {
        assertThat(javaSources()
                        .filter(ContentPublishingDiscoveryBoundaryArchitectureTest::containsForbiddenDiscoveryOwnership))
                .isEmpty();
    }

    @Test
    void contentDiscoverabilityCoordinatorHasNoProductionNoopFactory() throws IOException {
        assertThat(javaSources()
                        .filter(ContentPublishingDiscoveryBoundaryArchitectureTest::containsProductionNoopCoordinator))
                .isEmpty();
    }

    private static Stream<Path> javaSources() throws IOException {
        return Files.walk(PRODUCTION_SOURCES)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static Stream<String> discoveryImports(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import dev.persefonia.discovery."))
                    .map(line -> line.substring("import ".length(), line.length() - 1));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }

    private static boolean isForbiddenDiscoveryImport(String importedClass) {
        return !(importedClass.startsWith("dev.persefonia.discovery.application.port.")
                || importedClass.startsWith("dev.persefonia.discovery.application.projection.")
                || importedClass.startsWith("dev.persefonia.discovery.application.redirect.")
                || importedClass.startsWith("dev.persefonia.discovery.application.route.")
                || importedClass.startsWith("dev.persefonia.discovery.application.contract."));
    }

    private static boolean containsForbiddenDiscoveryOwnership(Path source) {
        String sourceText = readString(source);
        return DISCOVERABLE_RESOURCE_CONSTRUCTION.matcher(sourceText).find()
                || sourceText.contains("discovery.discoverable_resources")
                || sourceText.contains("discovery.redirect_rules");
    }

    private static boolean containsProductionNoopCoordinator(Path source) {
        return PRODUCTION_NOOP_COORDINATOR.matcher(readString(source)).find();
    }

    private static String readString(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }
}

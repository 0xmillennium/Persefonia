package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DiscoveryBoundaryArchitectureTest {
    private static final Pattern DISCOVERABLE_RESOURCE_CONSTRUCTION =
            Pattern.compile("\\bnew\\s+DiscoverableResource\\s*\\(");

    @Test
    void discoveryProductionCodeRemainsFrameworkFreeAndContextIndependent() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.discovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "javax.servlet..",
                        "jakarta.servlet..",
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "dev.persefonia.app..",
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..")
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentPublishingDoesNotDependOnDiscoveryDomainInfrastructureOrRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.discovery.application.repository..",
                        "dev.persefonia.app.discovery.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void discoveryPersistenceIsOwnedByAppAndDoesNotLeakToSourceOrWebContexts() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.app.discovery.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webPublicDoesNotDependOnRepositoriesOrAppPersistence() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain.port..",
                        "dev.persefonia.discovery.application.repository..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.app..persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void sourceContextsAndCompositionLayersDoNotConstructDiscoverableResource() throws IOException {
        try (Stream<Path> productionSources = Files.walk(Path.of(".."))) {
            assertThat(productionSources
                            .filter(path -> path.toString().contains("/src/main/java/"))
                            .filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.toString().contains("/discovery/"))
                            .filter(DiscoveryBoundaryArchitectureTest::constructsDiscoverableResource))
                    .isEmpty();
        }
    }

    @Test
    void sourceAndWebContextsDoNotWriteDiscoveryTablesDirectly() throws IOException {
        try (Stream<Path> productionSources = Files.walk(Path.of(".."))) {
            assertThat(productionSources
                            .filter(path -> path.toString().contains("/src/main/java/"))
                            .filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> path.toString().contains("/content-publishing/")
                                    || path.toString().contains("/web-public/"))
                            .filter(DiscoveryBoundaryArchitectureTest::referencesDiscoveryTable))
                    .isEmpty();
        }
    }

    private static boolean constructsDiscoverableResource(Path source) {
        try {
            return DISCOVERABLE_RESOURCE_CONSTRUCTION.matcher(Files.readString(source)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }

    private static boolean referencesDiscoveryTable(Path source) {
        try {
            String sourceText = Files.readString(source);
            return sourceText.contains("discovery.discoverable_resources")
                    || sourceText.contains("discovery.redirect_rules");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }
}

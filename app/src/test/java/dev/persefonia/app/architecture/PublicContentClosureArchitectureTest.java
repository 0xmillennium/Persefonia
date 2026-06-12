package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class PublicContentClosureArchitectureTest {
    @Test
    void publishingAndAdminDoNotConstructOrPersistDiscoveryResources() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.app.contentpublishing..",
                        "dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.discovery..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class PublishingDiscoveryBoundaryArchitectureTest {
    @Test
    void publishingAndAdminDoNotConstructOrPersistDiscoveryResources() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.app.contentpublishing..",
                        "dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.discovery.application.service..",
                        "dev.persefonia.app.discovery.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

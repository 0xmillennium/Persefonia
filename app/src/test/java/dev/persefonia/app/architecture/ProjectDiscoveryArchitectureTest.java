package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import org.junit.jupiter.api.Test;

class ProjectDiscoveryArchitectureTest {
    @Test
    void profilePortfolioProjectDiscoveryUsesOnlyDiscoveryApplicationPortsAndContracts() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.application.discovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.discovery.application.service..",
                        "dev.persefonia.discovery.application.redirect..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void projectDiscoveryDoesNotConstructDiscoveryAggregatesOrRedirects() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.app.profileportfolio..",
                        "dev.persefonia.webpublic.projects..")
                .should().dependOnClassesThat().areAssignableTo(DiscoverableResource.class)
                .orShould().dependOnClassesThat().areAssignableTo(DiscoverableResourceRepository.class)
                .orShould().dependOnClassesThat().areAssignableTo(CreateRedirectRulePort.class)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

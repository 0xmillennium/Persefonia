package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class TaxonomyArchitectureTest {
    @Test
    void taxonomyDomainHasNoFrameworkJdbcWebOrOtherContextDependency() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.taxonomy.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.servlet..",
                        "dev.persefonia.discovery..",
                        "dev.persefonia.contentpublishing..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminDoesNotDependOnTagRepositoryOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().haveSimpleName("TagRepository")
                .orShould().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void taxonomyDoesNotDependOnDiscoveryDomainPersistenceOrServicesAndContentPublishingAvoidsTaxonomyInfrastructure() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.taxonomy..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.application.service..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.app.discovery..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.app.taxonomy.persistence..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentPublishingDoesNotAccessTaxonomyPersistenceOrTagRepository() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.taxonomy.persistence..",
                        "dev.persefonia.taxonomy.domain.port..")
                .orShould().dependOnClassesThat().haveSimpleName("TagRepository")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void taxonomyDoesNotDependOnContentPublishing() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.taxonomy..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.contentpublishing..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentTagAssignmentDoesNotTouchDiscovery() {
        noClasses()
                .that().haveSimpleNameContaining("ContentTagAssignment")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.discovery..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

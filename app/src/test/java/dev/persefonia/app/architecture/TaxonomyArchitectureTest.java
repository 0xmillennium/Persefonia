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
    void taxonomyDoesNotDependOnDiscoveryAndContentPublishingDoesNotDependOnTaxonomyInfrastructure() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.taxonomy..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.discovery..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.app.taxonomy.persistence..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class CompositionBoundaryArchitectureTest {
    @Test
    void appDoesNotContainFeatureApplicationServices() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleNameEndingWith("ApplicationService")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appDoesNotContainProductionFeatureCommandHandlers() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleNameEndingWith("CommandHandler")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appDoesNotContainProductionFeatureAdminControllers() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleNameStartingWith("Admin")
                .andShould().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appIdentityAccessBootstrapContainsOnlyAdaptersConfigurationAndTransactionBoundary() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app.identityaccess.bootstrap..")
                .should().haveSimpleNameEndingWith("Service")
                .orShould().haveSimpleNameEndingWith("UseCase")
                .orShould().haveSimpleNameEndingWith("CommandHandler")
                .orShould().haveSimpleNameEndingWith("ApplicationService")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminDoesNotDependOnApp() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.app..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminDoesNotCallRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminDoesNotDependOnContentPersistenceOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.contentpublishing.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void identityAccessProductionCodeIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.identityaccess..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.security..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentPublishingProductionCodeDoesNotDependOnSpringDataJdbcOrSqlApis() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "org.flywaydb..",
                        "java.sql..",
                        "javax.sql..",
                        "dev.persefonia.app..",
                        "dev.persefonia.webadmin..",
                        "dev.persefonia.webpublic..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

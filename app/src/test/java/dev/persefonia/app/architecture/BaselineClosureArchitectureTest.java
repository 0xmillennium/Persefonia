package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class BaselineClosureArchitectureTest {
    @Test
    void appDoesNotContainFeatureApplicationServices() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleName("ContentApplicationService")
                .orShould().haveSimpleName("ProjectApplicationService")
                .orShould().haveSimpleName("MediaApplicationService")
                .orShould().haveSimpleName("ContactApplicationService")
                .orShould().haveSimpleName("AnalyticsApplicationService")
                .orShould().haveSimpleName("AuditApplicationService")
                .orShould().haveSimpleName("SettingsApplicationService")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appDoesNotContainProductionFeatureCommandHandlers() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleName("ContentCommandHandler")
                .orShould().haveSimpleName("ProjectCommandHandler")
                .orShould().haveSimpleName("MediaCommandHandler")
                .orShould().haveSimpleName("ContactCommandHandler")
                .orShould().haveSimpleName("AnalyticsCommandHandler")
                .orShould().haveSimpleName("AuditCommandHandler")
                .orShould().haveSimpleName("SettingsCommandHandler")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appDoesNotContainProductionFeatureAdminControllers() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleName("AdminContentController")
                .orShould().haveSimpleName("AdminProjectController")
                .orShould().haveSimpleName("AdminMediaController")
                .orShould().haveSimpleName("AdminContactController")
                .orShould().haveSimpleName("AdminAnalyticsController")
                .orShould().haveSimpleName("AdminAuditController")
                .orShould().haveSimpleName("AdminSettingsController")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminBootstrapServiceIsTheOnlyDocumentedAppApplicationServiceException() {
        classes()
                .that().resideInAPackage("dev.persefonia.app..")
                .and().haveSimpleNameEndingWith("Service")
                .should().haveSimpleName("AdminBootstrapService")
                .orShould().resideInAnyPackage(
                        "dev.persefonia.app.security..",
                        "dev.persefonia.app.assets..",
                        "dev.persefonia.app.web..",
                        "dev.persefonia.app.webadmin..")
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
    void identityAccessHasNoSpringSecurityOrJdbcDependency() {
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
}

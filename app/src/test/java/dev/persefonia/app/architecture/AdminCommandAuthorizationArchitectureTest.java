package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminCommandAuthorizationArchitectureTest {
    @Test
    void identityAccessCommandAuthorizationPackageHasNoFrameworkJdbcOrWebDependency() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.identityaccess.application.admin.authorization..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.security..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate..",
                        "reactor..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appCommandActorResolverDoesNotDependOnRepositoriesOrBootstrapService() {
        noClasses()
                .that().haveSimpleName("PersefoniaAdminCommandActorResolver")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.identityaccess.bootstrap..",
                        "dev.persefonia.app.identityaccess.persistence..",
                        "org.springframework.jdbc..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void outOfScopeProductionFeatureAdminControllersDoNotExist() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .doesNotContain(
                        "AdminContentController",
                        "AdminSettingsController");
    }

    @Test
    void auditAdminControllerIsAllowedAfterReadOnlyAuditImplementation() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .contains("AdminAuditController");
    }

    @Test
    void analyticsAdminControllerIsAllowedAfterAnalyticsImplementation() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .contains("AdminAnalyticsController");
    }

    @Test
    void projectAdminControllerIsAllowedAfterProjectAdminImplementation() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .contains("AdminProjectController");
    }

    @Test
    void mediaAdminControllerIsAllowedAfterMediaAdminImplementation() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .contains("AdminMediaController");
    }

    @Test
    void contactAdminControllerIsAllowedAfterContactAdminImplementation() {
        assertThat(ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                        .map(javaClass -> javaClass.getSimpleName()))
                .contains("AdminContactController");
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class CacheInvalidationArchitectureTest {
    private static final String CACHE_DOMAIN = "dev.persefonia.platformoperations.domain.cache..";
    private static final String CACHE_APPLICATION = "dev.persefonia.platformoperations.application.cache..";
    private static final String CACHE_PERSISTENCE = "dev.persefonia.app.platformoperations.cache..";

    @Test
    void domainAndApplicationAreFrameworkAndProviderImplementationIndependent() {
        noClasses().that().resideInAnyPackage(CACHE_DOMAIN, CACHE_APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "javax.servlet..", "java.sql..", "javax.sql..",
                        "org.flywaydb..", "org.postgresql..", "java.net.http..", "org.springframework.web..",
                        "dev.persefonia.app..", "dev.persefonia.webadmin..", "dev.persefonia.webpublic..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void cacheModelDoesNotDependOnSourceContextsOrPublicEligibilityTypes() {
        noClasses().that().resideInAnyPackage(CACHE_DOMAIN, CACHE_APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.contentpublishing..", "dev.persefonia.profileportfolio..",
                        "dev.persefonia.medialibrary..", "dev.persefonia.discovery..",
                        "dev.persefonia.audit..", "dev.persefonia.communication..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void aggregateChildrenHaveNoRepositories() {
        noClasses().that().resideInAPackage("dev.persefonia.platformoperations..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().haveSimpleNameContaining("Target")
                .orShould().haveSimpleNameContaining("Attempt")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void aggregateRepositoryImplementationLivesInAppInfrastructure() {
        classes().that().implement("dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository")
                .should().resideInAPackage(CACHE_PERSISTENCE)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

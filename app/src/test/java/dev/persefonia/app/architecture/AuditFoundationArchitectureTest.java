package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class AuditFoundationArchitectureTest {
    private static final String AUDIT_DOMAIN = "dev.persefonia.audit.domain..";
    private static final String AUDIT_APPLICATION = "dev.persefonia.audit.application..";
    private static final String AUDIT_PERSISTENCE = "dev.persefonia.app.audit.persistence..";

    @Test
    void auditDomainHasNoFrameworkOrInfrastructureDependency() {
        noClasses()
                .that().resideInAPackage(AUDIT_DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "gg.jte..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "org.flywaydb..",
                        "org.postgresql..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void auditApplicationDoesNotDependOnCompositionOrWebOrSourceContexts() {
        noClasses()
                .that().resideInAPackage(AUDIT_APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..",
                        "dev.persefonia.identityaccess..",
                        "dev.persefonia.taxonomy..",
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.communication..",
                        "dev.persefonia.discovery..",
                        "dev.persefonia.contentintegrity..",
                        "dev.persefonia.insights..",
                        "dev.persefonia.portability..",
                        "dev.persefonia.platformoperations..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webModulesDoNotDependOnAuditPersistence() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.webpublic..", "dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAPackage(AUDIT_PERSISTENCE)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void sourceContextsDoNotDependOnAuditPersistence() {
        noClasses()
                .that().resideInAnyPackage(ArchitectureTestSupport.BOUNDED_CONTEXT_PACKAGES)
                .should().dependOnClassesThat().resideInAPackage(AUDIT_PERSISTENCE)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void noAuditChildRepositoryExists() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.audit..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().haveSimpleNameContaining("Change")
                .orShould().haveSimpleNameContaining("Metadata")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void auditRecordRepositoryIsOnlyImplementedUnderAppAuditPersistence() {
        classes()
                .that().implement("dev.persefonia.audit.domain.record.port.AuditRecordRepository")
                .should().resideInAPackage(AUDIT_PERSISTENCE)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

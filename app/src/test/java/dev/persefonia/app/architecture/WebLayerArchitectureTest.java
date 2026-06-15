package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class WebLayerArchitectureTest {
    @Test
    void publicWebLayerDoesNotReachIntoCompositionContextsOrPersistence() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.webadmin..",
                        "dev.persefonia.identityaccess..",
                        "dev.persefonia.taxonomy..",
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.contentpublishing.domain.revision..",
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.communication..",
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.application.service..",
                        "dev.persefonia.discovery.application.projection..",
                        "dev.persefonia.discovery.application.redirect..",
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.contentintegrity..",
                        "dev.persefonia.insights..",
                        "dev.persefonia.audit..",
                        "dev.persefonia.portability..",
                        "dev.persefonia.platformoperations..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "org.flywaydb..",
                        "org.postgresql..",
                        "io.lettuce..",
                        "redis.clients..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminWebLayerDoesNotReachIntoCompositionContextsOrPersistence() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.identityaccess..",
                        "dev.persefonia.taxonomy.domain.port..",
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.contentpublishing.domain.revision..",
                        "dev.persefonia.contentpublishing.infrastructure..",
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.communication..",
                        "dev.persefonia.discovery..",
                        "dev.persefonia.contentintegrity..",
                        "dev.persefonia.insights..",
                        "dev.persefonia.audit..",
                        "dev.persefonia.portability..",
                        "dev.persefonia.platformoperations..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "org.flywaydb..",
                        "org.postgresql..",
                        "io.lettuce..",
                        "redis.clients..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminWebLayerDoesNotExposeRestControllers() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().beAnnotatedWith(RestController.class)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
                        "dev.persefonia.contentpublishing..",
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
                        "redis.clients..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminWebLayerRemainsControllerAndRouteFree() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().beAnnotatedWith(Controller.class)
                .orShould().beAnnotatedWith(RestController.class)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        methods()
                .that().areDeclaredInClassesThat().resideInAPackage("dev.persefonia.webadmin..")
                .should().notBeAnnotatedWith(RequestMapping.class)
                .andShould().notBeAnnotatedWith(GetMapping.class)
                .andShould().notBeAnnotatedWith(PostMapping.class)
                .andShould().notBeAnnotatedWith(PutMapping.class)
                .andShould().notBeAnnotatedWith(PatchMapping.class)
                .andShould().notBeAnnotatedWith(DeleteMapping.class)
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

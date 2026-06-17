package dev.persefonia.app.webadmin.projects;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.persefonia.profileportfolio.application.service.ProjectCommandGateway;
import dev.persefonia.profileportfolio.application.service.ProjectCommandService;
import dev.persefonia.webadmin.projects.AdminProjectController;
import org.junit.jupiter.api.Test;

class AdminProjectArchitectureTest {
    @Test
    void adminProjectControllerDependsOnCommandGateway() {
        assertThat(AdminProjectController.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(ProjectCommandGateway.class)
                        .doesNotContain(ProjectCommandService.class));
    }

    @Test
    void adminProjectWebDoesNotBypassApplicationServicesOrReachPersistence() {
        var classes = new ClassFileImporter().importPackages("dev.persefonia.webadmin.projects");

        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.projects..")
                .should().dependOnClassesThat().haveFullyQualifiedName(ProjectCommandService.class.getName())
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.profileportfolio.domain..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(classes);
    }
}

package dev.persefonia.app.webadmin.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import dev.persefonia.webadmin.content.AdminContentDraftController;
import dev.persefonia.webadmin.content.AdminContentEditController;
import dev.persefonia.webadmin.content.AdminContentLifecycleController;
import dev.persefonia.webadmin.content.AdminContentPreviewController;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminContentCommandBoundaryArchitectureTest {
    private static final String REASON =
            "Admin web command paths must go through the transactional command gateway abstraction.";

    @Test
    void adminContentControllersDependOnCommandGateway() {
        List<Class<?>> commandControllers = List.of(
                AdminContentDraftController.class,
                AdminContentEditController.class,
                AdminContentLifecycleController.class,
                AdminContentPreviewController.class);

        assertThat(commandControllers)
                .allSatisfy(controller -> assertThat(controller.getDeclaredConstructors())
                        .singleElement()
                        .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                                .contains(ContentCommandGateway.class)
                                .doesNotContain(ContentCommandService.class)));
    }

    @Test
    void adminContentControllersDoNotBypassCommandGatewayOrReachPersistence() {
        var classes = new ClassFileImporter().importPackages("dev.persefonia.webadmin.content");

        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.content..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveFullyQualifiedName(ContentCommandService.class.getName())
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.contentpublishing.domain.revision.port..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .because(REASON)
                .allowEmptyShould(true)
                .check(classes);
    }
}

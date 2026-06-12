package dev.persefonia.app.webadmin.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.webadmin.content.AdminContentRevisionController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class AdminContentRevisionArchitectureTest {
    @Test
    void revisionControllerUsesApplicationQueryApiAndExposesOnlyGet() {
        assertThat(AdminContentRevisionController.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(ContentRevisionQueryHandler.class));
        assertThat(Arrays.stream(AdminContentRevisionController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(PostMapping.class)))
                .isEmpty();
        assertThat(Arrays.stream(AdminContentRevisionController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .hasSize(1);
    }

    @Test
    void revisionWebCodeDoesNotReachRepositoriesPersistenceOrJdbc() {
        var classes = new ClassFileImporter().importPackages("dev.persefonia.webadmin.content");

        noClasses()
                .that().haveSimpleNameContaining("Revision")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.contentpublishing.domain.revision..",
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.app..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(classes);
    }
}

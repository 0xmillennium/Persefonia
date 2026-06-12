package dev.persefonia.app.webadmin.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.persefonia.webadmin.content.AdminContentLifecycleController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class AdminContentLifecycleArchitectureTest {
    @Test
    void lifecycleWebCodeDoesNotDependOnRepositoriesPersistenceOrJdbc() {
        var classes = new ClassFileImporter().importPackages("dev.persefonia.webadmin.content");

        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.content..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.contentpublishing.domain.revision.port..",
                        "dev.persefonia.app.contentpublishing.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void lifecycleControllerExposesOnlyPostMutations() {
        var lifecycleMethods = Arrays.stream(AdminContentLifecycleController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertThat(lifecycleMethods).hasSize(3);
        assertThat(lifecycleMethods).allMatch(method -> method.isAnnotationPresent(PostMapping.class));
    }
}

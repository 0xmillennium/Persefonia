package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class ForbiddenTechnologyArchitectureTest {
    @Test
    void productionCodeDoesNotDependOnForbiddenTechnologies() {
        noClasses()
                .that().resideInAPackage("dev.persefonia..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.web.reactive..",
                        "reactor.core..",
                        "lombok..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

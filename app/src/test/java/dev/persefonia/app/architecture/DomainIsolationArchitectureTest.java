package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class DomainIsolationArchitectureTest {
    @Test
    void boundedContextsDoNotDependOnFrameworkOrInfrastructureApis() {
        noClasses()
                .that().resideInAnyPackage(ArchitectureTestSupport.BOUNDED_CONTEXT_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "gg.jte..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "org.flywaydb..",
                        "org.postgresql..",
                        "io.lettuce..",
                        "redis.clients..",
                        "io.micrometer..",
                        "org.hibernate..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "reactor.core..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

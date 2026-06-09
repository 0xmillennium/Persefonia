package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class ApplicationCompositionArchitectureTest {
    @Test
    void appPackageContainsNoFeatureLogicByNamingConvention() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app..")
                .should().haveSimpleNameEndingWith("Controller")
                .orShould().haveSimpleNameEndingWith("Repository")
                .orShould().haveSimpleNameEndingWith("JdbcRepository")
                .orShould().haveSimpleNameEndingWith("JpaRepository")
                .orShould().haveSimpleNameEndingWith("ApplicationService")
                .orShould().haveSimpleNameEndingWith("CommandHandler")
                .orShould().haveSimpleNameEndingWith("Aggregate")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

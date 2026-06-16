package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProfilePortfolioArchitectureTest {
    @Test
    void profilePortfolioDomainDoesNotDependOnFrameworkJdbcOrWebApis() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioApplicationDoesNotDependOnFrameworkJdbcOrWebApis() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioContextDoesNotDependOnDiscoveryTaxonomyOrMediaImplementations() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.app.profileportfolio..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.infrastructure..",
                        "dev.persefonia.app.discovery.persistence..",
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.taxonomy.domain.port..",
                        "dev.persefonia.app.taxonomy.persistence..",
                        "dev.persefonia.medialibrary.infrastructure..",
                        "dev.persefonia.app.medialibrary.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void appProfilePortfolioApplicationDoesNotDependOnDiscoveryTaxonomyOrMediaRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app.profileportfolio.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery..",
                        "dev.persefonia.taxonomy..",
                        "dev.persefonia.medialibrary..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminProfileDoesNotDependOnRepositoriesOrAdapters() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.profile..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.profileportfolio.domain.profile..",
                        "dev.persefonia.profileportfolio.domain.project..",
                        "dev.persefonia.profileportfolio.domain.settings..",
                        "dev.persefonia.app.profileportfolio.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webPublicHomepageDoesNotDependOnRepositoriesOrAdapters() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.profileportfolio.domain.profile..",
                        "dev.persefonia.profileportfolio.domain.project..",
                        "dev.persefonia.app.profileportfolio.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioDoesNotConstructOrMutateDiscoverableResource() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.app.profileportfolio..")
                .should().dependOnClassesThat().haveFullyQualifiedName("dev.persefonia.discovery.domain.DiscoverableResource")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void projectDomainDoesNotDependOnProfileDomain() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.domain.project..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.profileportfolio.domain.profile..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void childEntityRepositoriesDoNotExist() {
        Set<String> forbiddenNames = Set.of(
                name("Profile", "Localization"),
                name("External", "Profile", "Link"),
                name("Technical", "Focus", "Area"),
                name("Education", "Summary"),
                name("Current", "Focus", "Item"),
                name("Project", "Localization"),
                name("Project", "Technology"),
                name("Project", "Link"),
                name("Project", "Case", "Study", "Section"),
                name("Project", "Tag"));

        Set<String> presentForbiddenNames = ArchitectureTestSupport.PRODUCTION_CLASSES.stream()
                .map(javaClass -> javaClass.getSimpleName())
                .filter(forbiddenNames::contains)
                .collect(Collectors.toSet());

        assertThat(presentForbiddenNames).isEmpty();
    }

    private static String name(String... parts) {
        return String.join("", parts) + "Repository";
    }
}

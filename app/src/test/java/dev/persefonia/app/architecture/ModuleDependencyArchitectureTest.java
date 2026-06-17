package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ModuleDependencyArchitectureTest {
    @Test
    void sharedKernelDependsOnlyOnSafeJavaLevelApis() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.shared..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "dev.persefonia.shared..",
                        "java..",
                        "javax..",
                        "jakarta.annotation..",
                        "org.jspecify..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void boundedContextsDoNotDependOnOtherContextsOrCompositionLayers() {
        for (String contextPackage : ArchitectureTestSupport.BOUNDED_CONTEXT_PACKAGES) {
            String[] forbiddenPackages = Arrays.stream(ArchitectureTestSupport.BOUNDED_CONTEXT_PACKAGES)
                    .filter(candidate -> !candidate.equals(contextPackage))
                    .filter(candidate -> allowedApplicationContractException(contextPackage, candidate))
                    .toArray(String[]::new);

            noClasses()
                    .that().resideInAPackage(contextPackage)
                    .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                    .allowEmptyShould(true)
                    .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

            noClasses()
                    .that().resideInAPackage(contextPackage)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.persefonia.app..",
                            "dev.persefonia.webpublic..",
                            "dev.persefonia.webadmin..")
                    .allowEmptyShould(true)
                    .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
        }
    }

    private static boolean allowedApplicationContractException(String contextPackage, String candidate) {
        boolean discoveryContractConsumer =
                (contextPackage.equals("dev.persefonia.contentpublishing..")
                                || contextPackage.equals("dev.persefonia.taxonomy..")
                                || contextPackage.equals("dev.persefonia.profileportfolio.."))
                        && candidate.equals("dev.persefonia.discovery..");
        return !discoveryContractConsumer;
    }
}

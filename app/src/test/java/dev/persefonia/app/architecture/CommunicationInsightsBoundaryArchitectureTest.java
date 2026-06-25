package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class CommunicationInsightsBoundaryArchitectureTest {
    @Test
    void communicationDomainAndApplicationStayFrameworkFree() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.communication.domain..", "dev.persefonia.communication.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void insightsDomainAndApplicationStayAwayFromRequestAndSessionApis() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.insights.domain..", "dev.persefonia.insights.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "jakarta.servlet.http..",
                        "javax.servlet.http..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.service.PublicContentQueryHandler;
import dev.persefonia.webpublic.content.PublicContentController;
import dev.persefonia.webpublic.content.PublicContentRouteParser;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PublicContentArchitectureTest {
    @Test
    void publicContentControllerUsesOnlyRouteParserQueryHandlerAndViewFactory() {
        assertThat(PublicContentController.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(
                                PublicContentRouteParser.class,
                                PublicContentQueryHandler.class,
                                PublicContentViewModelFactory.class));
    }

    @Test
    void webPublicContentDoesNotDependOnPersistenceInfrastructureOrAppComposition() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic.content..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.contentpublishing.domain.content.port..",
                        "dev.persefonia.contentpublishing.domain.revision.port..",
                        "dev.persefonia.app.contentpublishing.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.hibernate..")
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webPublicContentDoesNotDependOnRenderingOrDiscoveryImplementations() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic.content..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.contentpublishing.application.rendering..",
                        "dev.persefonia.app.contentpublishing.rendering..",
                        "dev.persefonia.discovery..",
                        "dev.persefonia.contentintegrity..",
                        "dev.persefonia.insights..",
                        "dev.persefonia.audit..")
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentPublishingProductionCodeRemainsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "dev.persefonia.app..",
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..")
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void publicContentTemplateReceivesViewModelAndHasSingleRawBodyOutput() throws Exception {
        String template = Files.readString(Path.of("src/main/jte/site/content.jte"));

        assertThat(template).contains("PublicContentPage");
        assertThat(template).doesNotContain("ContentItem");
        assertThat(template).doesNotContain("ContentRevision");
        assertThat(Arrays.stream(template.split("\\R"))
                .filter(line -> line.contains("$unsafe{"))
                .count()).isEqualTo(1);
        assertThat(template).contains("$unsafe{page.renderedHtml()}");
    }
}

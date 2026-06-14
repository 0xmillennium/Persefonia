package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.webpublic.content.DiscoveryPublicContentRouteResolver;
import dev.persefonia.webpublic.content.PublicContentController;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentRouteParser;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PublicContentArchitectureTest {
    @Test
    void publicContentControllerUsesOnlyRouteParserQueryHandlerAndViewFactory() {
        assertThat(PublicContentController.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(
                                PublicContentRouteParser.class,
                                DiscoveryPublicContentRouteResolver.class,
                                PublicContentBySourceQueryHandler.class,
                                PublicContentViewModelFactory.class,
                                PublicContentResponseHeaders.class));
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
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.discovery.application.service..",
                        "dev.persefonia.discovery.application.projection..",
                        "dev.persefonia.discovery.application.redirect..",
                        "dev.persefonia.discovery.infrastructure..",
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
        assertThat(template).contains("@if(page.mermaidScriptPath().isPresent())");
        assertThat(template).contains("page.headings()");
        assertThat(template).doesNotContain("markdownSource");
        assertThat(template).doesNotContain("/admin/content");
        assertThat(template).doesNotContain("/preview");
        assertThat(template).doesNotContain("/revisions");
    }

    @Test
    void publicFrontendKeepsMermaidOutOfGlobalBundle() throws Exception {
        String mainEntry = Files.readString(Path.of("../frontend/src/main.ts"));
        String mermaidEntry = Files.readString(Path.of("../frontend/src/mermaid-loader.ts"));

        assertThat(mainEntry).doesNotContain("mermaid");
        assertThat(mermaidEntry).contains("from \"mermaid\"");
    }

    @Test
    void publicContentRoutesDoNotExposePostHandlers() throws Exception {
        try (var paths = Files.walk(Path.of("../web-public/src/main/java/dev/persefonia/webpublic"))) {
            String routeAnnotations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (java.io.IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .reduce("", String::concat);

            assertThat(routeAnnotations).doesNotContain("@PostMapping");
            assertThat(routeAnnotations).doesNotContain("method = RequestMethod.POST");
        }
    }

    @Test
    void publicSecurityMatcherDoesNotUseBroadThreeSegmentPermit() throws Exception {
        String securityConfiguration = Files.readString(Path.of("src/main/java/dev/persefonia/app/security/SecurityConfiguration.java"));

        assertThat(securityConfiguration).doesNotContain("\"/*/*/*\"");
        assertThat(securityConfiguration).contains("PUBLIC_CONTENT_GET_PATTERNS");
        assertThat(securityConfiguration).contains("/tr/articles/*");
        assertThat(securityConfiguration).contains("/en/pages/*");
    }

    @Test
    void testSupportDoesNotConflateContentRepositoryAndDiscoveryRoutePorts() {
        var classes = new ClassFileImporter().importPackages("dev.persefonia");
        assertThat(classes.stream().map(JavaClass::getName))
                .contains("dev.persefonia.app.webpublic.content.PublicContentTestRepository");

        assertThat(classes.stream()
                        .filter(javaClass -> javaClass.isAssignableTo(ContentItemRepository.class))
                        .filter(javaClass -> javaClass.isAssignableTo(ResolvePublicRoutePort.class))
                        .map(JavaClass::getName))
                .isEmpty();
    }

    @Test
    void testSupportDoesNotUseOmnibusPublicRouteFakesAcrossBoundedContextPorts() {
        assertThat(testJavaSources().stream()
                        .filter(path -> implementedForbiddenPortCount(path) > 1)
                        .map(Path::toString))
                .isEmpty();
    }

    private static List<Path> testJavaSources() {
        List<Path> sources = new ArrayList<>();
        for (Path root : List.of(
                Path.of("src/test/java"),
                Path.of("../web-public/src/test/java"),
                Path.of("../content-publishing/src/test/java"))) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return sources;
    }

    private static long implementedForbiddenPortCount(Path sourcePath) {
        String source;
        try {
            source = Files.readString(sourcePath);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return List.of(
                        "ContentItemRepository",
                        "UpdateDiscoverableResourcePort",
                        "RemoveDiscoverableResourcePort",
                        "CreateRedirectRulePort",
                        "ResolvePublicRoutePort",
                        "DiscoverableResourceRepository",
                        "RedirectRuleRepository")
                .stream()
                .filter(port -> implementsPort(source, port))
                .count();
    }

    private static boolean implementsPort(String source, String port) {
        return Pattern.compile("\\bimplements\\b[^{};]*\\b" + Pattern.quote(port) + "\\b", Pattern.DOTALL)
                .matcher(source)
                .find();
    }
}

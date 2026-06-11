package dev.persefonia.app.contentpublishing.rendering;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarkdownRenderingBoundaryTest {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.persefonia");

    @Test
    void contentPublishingPortDoesNotDependOnRenderingLibrariesOrFrameworks() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.commonmark..", "org.jsoup..", "org.springframework..",
                        "org.springframework.data..", "org.springframework.jdbc..",
                        "java.sql..", "javax.sql..", "jakarta.persistence..", "javax.persistence..", "org.hibernate..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void rendererAndRepositoriesDoNotDependOnEachOther() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.app.contentpublishing.rendering..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
        noClasses()
                .that().haveSimpleNameEndingWith("Repository")
                .or().haveSimpleNameEndingWith("RepositoryAdapter")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.app.contentpublishing.rendering..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void webLayersDoNotDependOnMarkdownRenderer() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.webpublic..", "dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.contentpublishing.rendering..",
                        "dev.persefonia.contentpublishing.application.rendering..",
                        "org.commonmark..",
                        "org.jsoup..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersAndTemplatesDoNotPerformMarkdownRendering() throws IOException {
        assertThat(sourceText(projectRoot().resolve("web-public/src/main/java")))
                .doesNotContain("MarkdownRenderingService", "CommonmarkMarkdownRenderingService", "org.commonmark", "org.jsoup");
        assertThat(sourceText(projectRoot().resolve("web-admin/src/main/java")))
                .doesNotContain("MarkdownRenderingService", "CommonmarkMarkdownRenderingService", "org.commonmark", "org.jsoup");
        assertThat(sourceText(projectRoot().resolve("app/src/main/jte")))
                .doesNotContain("MarkdownRenderingService", "CommonmarkMarkdownRenderingService", "org.commonmark", "org.jsoup");
    }

    private static Path projectRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return workingDirectory.getFileName().toString().equals("app") ? workingDirectory.getParent() : workingDirectory;
    }

    private static String sourceText(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .map(MarkdownRenderingBoundaryTest::readString)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source boundary", exception);
        }
    }
}

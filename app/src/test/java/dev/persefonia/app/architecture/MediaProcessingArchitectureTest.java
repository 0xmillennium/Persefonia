package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MediaProcessingArchitectureTest {
    @Test
    void mediaDomainAndApplicationExcludeImageIoFilesystemAndFrameworks() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.medialibrary.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.imageio..",
                        "java.awt..",
                        "java.nio.file..",
                        "org.springframework..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "jakarta.servlet..",
                        "gg.jte..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void imageIoUsageIsConfinedToAppMediaProcessingAdapters() throws Exception {
        assertThat(productionSourcesContaining("ImageIO"))
                .allSatisfy(path -> assertThat(path.toString())
                        .contains("src/main/java/dev/persefonia/app/medialibrary/processing/"));
    }

    @Test
    void webPublicUsesNoMediaRepositoriesAdaptersOrFilesystem() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application.asset..",
                        "dev.persefonia.medialibrary.application.storage..",
                        "dev.persefonia.app.medialibrary..",
                        "java.nio.file..")
                .orShould().dependOnClassesThat().haveSimpleName("AssetRepository")
                .orShould().dependOnClassesThat().haveSimpleName("JdbcAssetRepositoryAdapter")
                .orShould().dependOnClassesThat().haveSimpleName("LocalFileAssetStorageAdapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void otherContextsAndAdminDoNotUseMediaProcessingStorageOrPublicServices() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.webadmin..",
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application.processing..",
                        "dev.persefonia.medialibrary.application.storage..",
                        "dev.persefonia.medialibrary.application.publicview..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void publicMediaRouteDoesNotExpandIntoProjectContentOrCvSurfaces() throws Exception {
        String projectAndContent =
                sourceText(Path.of("src/main/jte/site/projects"))
                        + sourceText(Path.of("src/main/jte/site/content"))
                        + sourceText(Path.of("../profile-portfolio/src/main/java"))
                        + sourceText(Path.of("../content-publishing/src/main/java"));
        assertThat(projectAndContent)
                .doesNotContain("PublicImageVariantView")
                .doesNotContain("PublicImageAssetQueryService")
                .doesNotContain("/media/assets");
        String projectTemplates = sourceText(Path.of("src/main/jte/site/projects"));
        assertThat(projectTemplates)
                .doesNotContain("coverAssetId")
                .doesNotContain("defaultOgImageAssetId")
                .doesNotContain("defaultOpenGraphImageAssetId");

        String publicMediaSources = sourceText(Path.of("../web-public/src/main/java/dev/persefonia/webpublic/media"));
        assertThat(publicMediaSources)
                .doesNotContain("ActiveCv")
                .doesNotContain("active_cv_profiles")
                .doesNotContain("cv_entries");
    }

    private static List<Path> productionSourcesContaining(String text) throws IOException {
        List<Path> roots = List.of(
                Path.of("src/main/java"),
                Path.of("../media-library/src/main/java"),
                Path.of("../web-public/src/main/java"));
        java.util.ArrayList<Path> matches = new java.util.ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> {
                            try {
                                return Files.readString(path).contains(text);
                            } catch (IOException exception) {
                                throw new IllegalStateException(exception);
                            }
                        })
                        .forEach(matches::add);
            }
        }
        return matches;
    }

    private static String sourceText(Path root) throws IOException {
        if (!Files.exists(root)) {
            return "";
        }
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }
}

package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MediaUploadArchitectureTest {
    @Test
    void mediaDomainDoesNotDependOnStreamingFilesystemOrFrameworkTypes() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.medialibrary.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.nio..",
                        "javax.imageio..",
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "gg.jte..",
                        "jakarta.servlet..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.InputStream")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void mediaApplicationDoesNotDependOnWebPersistenceOrLocalAdapterTypes() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.medialibrary.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.nio.file..",
                        "javax.imageio..",
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "gg.jte..",
                        "jakarta.servlet..",
                        "dev.persefonia.app.medialibrary.storage..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void localFilesystemAdapterExistsOnlyInTheAppStoragePackage() throws Exception {
        assertThat(productionJavaSourcesContaining("class LocalFileAssetStorageAdapter"))
                .singleElement()
                .satisfies(path -> assertThat(path.toString())
                        .contains("/app/src/main/java/dev/persefonia/app/medialibrary/storage/"));
    }

    @Test
    void uploadAndStorageServicesAreNotUsedByWebOrOtherContexts() throws Exception {
        for (String symbol : List.of("AssetStoragePort", "UploadAssetCommandService")) {
            assertThat(productionJavaSourcesContaining(symbol).stream()
                            .map(Path::toString)
                            .filter(path -> !path.contains("/media-library/src/main/java/"))
                            .filter(path -> !path.contains("/app/src/main/java/dev/persefonia/app/medialibrary/")))
                    .isEmpty();
        }
    }

    @Test
    void onlyPublicVariantMediaRouteAndNoCvRoutesAreIntroduced() throws Exception {
        String publicSources = sourceText(Path.of("../web-public/src/main/java"));
        String adminSources = sourceText(Path.of("../web-admin/src/main/java"));
        assertThat(publicSources)
                .doesNotContain("UploadAssetCommandService")
                .doesNotContain("AssetStoragePort")
                .doesNotContain("@PostMapping(\"/media")
                .doesNotContain("@GetMapping(\"/assets")
                .doesNotContain("@PostMapping(\"/assets")
                .doesNotContain("@GetMapping(\"/cv")
                .doesNotContain("@PostMapping(\"/cv")
                .doesNotContain("ActiveCv")
                .doesNotContain("cv_entries");
        assertThat(publicSources.split(
                        java.util.regex.Pattern.quote(
                                "@GetMapping(\"/media/assets/{assetId}/variants/{variantName}\")"),
                        -1))
                .hasSize(2);
        assertThat(adminSources)
                .doesNotContain("@GetMapping(\"/media")
                .doesNotContain("@PostMapping(\"/media")
                .doesNotContain("PublicImage")
                .doesNotContain("ProcessImageAsset");

        assertThat(Files.readString(Path.of("src/main/resources/db/migration/V16__active_cv_profile.sql")))
                .doesNotContain("cv_entries")
                .doesNotContain("REFERENCES media.assets");
    }

    private static List<Path> productionJavaSourcesContaining(String text) throws IOException {
        List<Path> roots = List.of(
                Path.of("../app/src/main/java"),
                Path.of("../media-library/src/main/java"),
                Path.of("../profile-portfolio/src/main/java"),
                Path.of("../content-publishing/src/main/java"),
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java"));
        List<Path> matches = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> contains(path, text))
                        .forEach(matches::add);
            }
        }
        return matches;
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
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

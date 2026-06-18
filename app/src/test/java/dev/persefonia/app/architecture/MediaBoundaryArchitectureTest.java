package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MediaBoundaryArchitectureTest {
    @Test
    void webLayersDoNotDependOnRepositoriesAdaptersOrMediaInternals() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..")
                .and().resideOutsideOfPackage("dev.persefonia.webadmin.media..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia..persistence..",
                        "dev.persefonia..infrastructure..",
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.app.medialibrary.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Adapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminMediaDoesNotDependOnMediaRepositoriesAdaptersOrFilesystemInternals() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.media..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia..persistence..",
                        "dev.persefonia..infrastructure..",
                        "dev.persefonia.app.medialibrary..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "javax.imageio..",
                        "java.nio.file..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Adapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioDoesNotDependOnMediaRepositoriesOrInfrastructure() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.app.profileportfolio..",
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.app.contentpublishing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application..",
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.medialibrary.infrastructure..",
                        "dev.persefonia.app.medialibrary.persistence..")
                .orShould().dependOnClassesThat().haveSimpleName("AssetRepository")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void mediaDomainAndApplicationRemainFrameworkAndAppFree() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.medialibrary.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "gg.jte..",
                        "jakarta.servlet..",
                        "dev.persefonia.app..",
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioDomainDoesNotDependOnMediaApplicationOrDomain() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application..",
                        "dev.persefonia.medialibrary.domain..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void projectTemplatesDoNotExposeCoverAssetInternals() throws Exception {
        for (Path root : List.of(
                Path.of("src/main/jte/site/projects"),
                Path.of("src/main/jte/admin/projects"))) {
            assertThat(sourceText(root))
                    .doesNotContain("coverAssetId")
                    .doesNotContain("defaultOgImageAssetId")
                    .doesNotContain("defaultOpenGraphImageAssetId");
        }
    }

    @Test
    void assetRepositoryUsageStaysInsideMediaCompositionBoundaries() throws Exception {
        assertThat(javaSourcesContaining("AssetRepository").stream()
                        .map(Path::toString)
                        .filter(path -> !path.contains("/media-library/src/main/java/"))
                        .filter(path -> !path.contains("/app/src/main/java/dev/persefonia/app/medialibrary/")))
                .isEmpty();
    }

    @Test
    void aggregateChildrenHaveNoRepositories() throws Exception {
        assertThat(javaSourcesContaining(childRepositorySymbol("AssetVariant"))).isEmpty();
        assertThat(javaSourcesContaining(childRepositorySymbol("AssetValidationResult"))).isEmpty();
    }

    @Test
    void appOwnsTheIntentionalJdbcAssetRepositoryAdapter() throws Exception {
        assertThat(javaSourcesContaining("class JdbcAssetRepositoryAdapter"))
                .singleElement()
                .satisfies(path -> assertThat(path.toString())
                        .contains("/app/src/main/java/dev/persefonia/app/medialibrary/persistence/"));
    }

    @Test
    void activeCvRoutesAndSchemaAreAbsentFromMediaWorkflow() throws Exception {
        String publicAndAdminSources = sourceText(Path.of("../web-public/src/main/java"))
                + sourceText(Path.of("../web-admin/src/main/java"))
                + sourceText(Path.of("src/main/jte/site"))
                + sourceText(Path.of("src/main/jte/admin"));

        assertThat(publicAndAdminSources)
                .doesNotContain("ActiveCv")
                .doesNotContain("ActiveCV")
                .doesNotContain("active-cv")
                .doesNotContain("cvDownload");
    }

    @Test
    void mediaWorkflowDoesNotIntroduceForbiddenBinaryOrLifecycleRoutes() throws Exception {
        String sources = sourceText(Path.of("../web-public/src/main/java"))
                + sourceText(Path.of("../web-admin/src/main/java"))
                + sourceText(Path.of("src/main/jte/admin"));

        assertThat(sources)
                .doesNotContain("/admin/media/{assetId}/" + "original")
                .doesNotContain("/admin/media/{assetId}/" + "preview")
                .doesNotContain("/admin/media/{assetId}/" + "variants")
                .doesNotContain("/admin/media/{assetId}/" + "delete")
                .doesNotContain("/admin/media/{assetId}/" + "reprocess")
                .doesNotContain("/media/assets/{assetId}/" + "original");
    }

    @Test
    void internalPlanningTermsAreAbsentFromMediaRelatedTests() throws Exception {
        String testSources = sourceText(Path.of("src/test"))
                + sourceText(Path.of("../media-library/src/test"))
                + sourceText(Path.of("../web-public/src/test"))
                + sourceText(Path.of("../web-admin/src/test"))
                + sourceText(Path.of("../profile-portfolio/src/test"));

        assertThat(testSources)
                .doesNotContain("Before" + "Milestone" + "8")
                .doesNotContain("Milestone" + "8")
                .doesNotContain("Ste" + "p" + "8");
    }

    private static List<Path> javaSourcesContaining(String text) throws IOException {
        List<Path> roots = List.of(
                Path.of("../app/src/main/java"),
                Path.of("../media-library/src/main/java"),
                Path.of("../profile-portfolio/src/main/java"),
                Path.of("../content-publishing/src/main/java"),
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java"));
        java.util.ArrayList<Path> matches = new java.util.ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
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

    private static String childRepositorySymbol(String aggregateChild) {
        return aggregateChild + "Repository";
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

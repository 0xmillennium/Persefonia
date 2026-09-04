package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ActiveCvArchitectureTest {
    @Test
    void profilePortfolioDomainDoesNotDependOnMediaLibrary() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.app.medialibrary..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void profilePortfolioApplicationDoesNotUseMediaRepositoriesStorageOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.profileportfolio.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.app.medialibrary..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..")
                .orShould().dependOnClassesThat().haveSimpleName("AssetRepository")
                .orShould().dependOnClassesThat().haveSimpleName("JdbcAssetRepositoryAdapter")
                .orShould().dependOnClassesThat().haveSimpleName("LocalFileAssetStorageAdapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminCvDoesNotDependOnMediaRepositoriesStorageImageIoOrProfileRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin.cv..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application.asset..",
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.app.medialibrary..",
                        "dev.persefonia.profileportfolio.domain..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "javax.imageio..",
                        "java.nio.file..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleName("LocalFileAssetStorageAdapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void activeCvSchemaHasNoPhysicalMediaForeignKeyOrForbiddenStoredData() throws Exception {
        String migrations = Files.readString(Path.of("src/main/resources/db/migration/V16__active_cv_profile.sql"));

        assertThat(migrations)
                .contains("active_cv_profiles")
                .contains("active_cv_documents")
                .doesNotContain("FOREIGN KEY (asset_id) REFERENCES media.assets")
                .doesNotContain("REFERENCES media.assets")
                .doesNotContain("storage_path")
                .doesNotContain("public_url")
                .doesNotContain("jsonb");
    }

    @Test
    void onlyControlledPublicCvRoutesExist() throws Exception {
        String publicSources = sourceText(Path.of("../web-public/src/main/java"))
                + sourceText(Path.of("src/main/jte/site"));

        assertThat(publicSources)
                .contains("@GetMapping(PublicCvRoutes.DEFAULT_PAGE)")
                .contains("@GetMapping(PublicCvRoutes.DEFAULT_DOWNLOAD)")
                .contains("@GetMapping(PublicCvRoutes.LANGUAGE_PAGE)")
                .contains("@GetMapping(PublicCvRoutes.LANGUAGE_DOWNLOAD)")
                .doesNotContain("@GetMapping(\"/resume")
                .doesNotContain("/media/assets/{assetId}/download")
                .doesNotContain("/media/assets/{assetId}/original");
    }

    @Test
    void webPublicCvDoesNotDependOnMediaStorageImageIoOrProfileRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic.cv..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.medialibrary.application.asset..",
                        "dev.persefonia.medialibrary.domain..",
                        "dev.persefonia.app.medialibrary..",
                        "dev.persefonia.profileportfolio.domain..",
                        "org.springframework.jdbc..",
                        "java.sql..",
                        "javax.sql..",
                        "javax.imageio..",
                        "java.nio.file..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleName("LocalFileAssetStorageAdapter")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
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

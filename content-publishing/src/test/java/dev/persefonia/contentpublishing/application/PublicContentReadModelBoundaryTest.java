package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PublicContentReadModelBoundaryTest {
    private static final Path PRODUCTION_ROOT = Path.of("src/main/java");
    private static final Path QUERY_PACKAGE = PRODUCTION_ROOT.resolve(
            "dev/persefonia/contentpublishing/application/query");
    private static final Path PUBLIC_HANDLER = PRODUCTION_ROOT.resolve(
            "dev/persefonia/contentpublishing/application/service/PublicContentQueryHandler.java");
    private static final List<Class<?>> PUBLIC_QUERY_TYPES = List.of(
            PublicContentRouteQuery.class,
            PublicContentHeadingResult.class,
            PublicContentPageResult.class,
            PublicContentLookupResult.Found.class,
            PublicContentLookupResult.NotFound.class);

    @Test
    void publicContentQueryHandlerHasNoSpringImports() throws IOException {
        assertThat(Files.readString(PUBLIC_HANDLER)).doesNotContain("org.springframework");
    }

    @Test
    void publicContentQueryReadModelsHaveNoSpringWebOrAppImports() throws IOException {
        for (Path file : javaFiles(QUERY_PACKAGE)) {
            String source = Files.readString(file);
            assertThat(source)
                    .doesNotContain("org.springframework")
                    .doesNotContain("jakarta.servlet")
                    .doesNotContain("javax.servlet")
                    .doesNotContain("dev.persefonia.app")
                    .doesNotContain("dev.persefonia.web");
        }
    }

    @Test
    void publicContentQueryReadModelsDoNotExposeSensitiveDomainTypes() {
        for (Class<?> type : PUBLIC_QUERY_TYPES) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertThat(component.getName())
                        .isNotIn("markdownSource", "source", "rawMarkdown", "unpublished" + "At", "version");
                assertThat(component.getGenericType().getTypeName())
                        .doesNotContain("Markdown" + "Source")
                        .doesNotContain("Content" + "Revision")
                        .doesNotContain("Admin" + "IdentityRef")
                        .doesNotEndWith(".Ver" + "sion");
            }
        }
    }

    @Test
    void contentPublishingProductionCodeStaysFrameworkFree() throws IOException {
        for (Path file : javaFiles(PRODUCTION_ROOT)) {
            String source = Files.readString(file);
            assertThat(source)
                    .doesNotContain("org.springframework")
                    .doesNotContain("org.springframework.data")
                    .doesNotContain("org.springframework.jdbc")
                    .doesNotContain("java.sql")
                    .doesNotContain("javax.sql")
                    .doesNotContain("jakarta.persistence")
                    .doesNotContain("javax.persistence")
                    .doesNotContain("org.hibernate")
                    .doesNotContain("dev.persefonia.app")
                    .doesNotContain("dev.persefonia.web");
        }
    }

    @Test
    void contentPublishingUsesOnlyDiscoveryApplicationPortsAndContracts() throws IOException {
        for (Path file : javaFiles(PRODUCTION_ROOT)) {
            String source = Files.readString(file);
            assertThat(source)
                    .doesNotContain("Discoverable" + "ResourceRepository")
                    .doesNotContain("dev.persefonia.discovery.domain")
                    .doesNotContain("dev.persefonia.discovery.infrastructure")
                    .doesNotContain("dev.persefonia.discovery.application.service")
                    .doesNotContain("dev.persefonia.app.discovery.persistence")
                    .doesNotContain("JdbcClient")
                    .doesNotContain("JdbcTemplate")
                    .doesNotContain("NamedParameterJdbcTemplate")
                    .doesNotContain("Resolve" + "PublicRoutePort")
                    .doesNotContain("new Discoverable" + "Resource(");
        }
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}

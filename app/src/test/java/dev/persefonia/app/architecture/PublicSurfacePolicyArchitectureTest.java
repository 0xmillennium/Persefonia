package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PublicSurfacePolicyArchitectureTest {
    private static final List<String> FORBIDDEN_PUBLIC_ROUTES = List.of(
            "/search/",
            "/search/**",
            "/sitemap.xml/**",
            "/robots.txt/**",
            "/sitemap_index.xml",
            "/feed.xml/**",
            "/rss.xml",
            "/atom.xml");
    private static final Pattern ROUTE_ANNOTATION = Pattern.compile(
            "@(?:GetMapping|PostMapping|RequestMapping)\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern ROUTE_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    void exposesExactCrawlerRoutesAndNoRssAtomOrWildcardRoutes() throws Exception {
        List<String> routeLiterals = routeLiterals(joinedJavaSources(Path.of("../web-public/src/main/java")));

        assertThat(routeLiterals)
                .doesNotContain(FORBIDDEN_PUBLIC_ROUTES.toArray(String[]::new));
        assertThat(routeLiterals).contains("/search", "/sitemap.xml", "/robots.txt", "/feed.xml");
    }

    @Test
    void doesNotIntroduceResumeOrGenericMediaAssetRoutes() throws Exception {
        List<String> routeLiterals = routeLiterals(joinedJavaSources(Path.of("../web-public/src/main/java")));

        assertThat(routeLiterals)
                .doesNotContain(
                        "/resume",
                        "/media/assets/{assetId}",
                        "/media/assets/{assetId}/download",
                        "/media/assets/{assetId}/original");
    }

    @Test
    void doesNotIntroduceSearchSchemaSearchEngineOrSearchTermPersistence() throws Exception {
        String productionAndMigrationText = joinedExistingSources(List.of(
                Path.of("../discovery/src/main/java"),
                Path.of("src/main/java"),
                Path.of("src/main/resources/db/migration"),
                Path.of("../insights/src/main/java")));

        assertThat(productionAndMigrationText)
                .doesNotContain("search_vector")
                .doesNotContain("SearchVector")
                .doesNotContain("GENERATED ALWAYS")
                .doesNotContain("SearchTerm")
                .doesNotContain("search_terms")
                .doesNotContain("Elasticsearch")
                .doesNotContain("OpenSearch");
    }

    @Test
    void doesNotCreateForbiddenCommittedDocumentationOrVerificationScripts() throws Exception {
        assertThat(Path.of("../docs/architecture")).doesNotExist();
        assertThat(Path.of("../docs/verification")).doesNotExist();
        assertThat(Path.of("../docs/testing")).doesNotExist();
        assertThat(Path.of("../docs/release")).doesNotExist();
        assertThat(Path.of("../docs/checklists")).doesNotExist();
        assertThat(Path.of("../docs/process")).doesNotExist();

        Path scripts = Path.of("../scripts");
        if (Files.exists(scripts)) {
            try (Stream<Path> paths = Files.walk(scripts, 2)) {
                assertThat(paths.filter(Files::isRegularFile).toList()).isEmpty();
            }
        }
    }

    private static String routeAnnotations(String source) {
        return ROUTE_ANNOTATION.matcher(source)
                .results()
                .map(match -> match.group(1))
                .reduce("", String::concat);
    }

    private static List<String> routeLiterals(String source) {
        return ROUTE_LITERAL.matcher(routeAnnotations(source))
                .results()
                .map(match -> match.group(1))
                .collect(Collectors.toList());
    }

    private static String joinedExistingSources(List<Path> roots) throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path root : roots) {
            if (Files.exists(root)) {
                joined.append(joinedJavaSources(root));
            }
        }
        return joined.toString();
    }

    private static String joinedJavaSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".sql"))
                    .map(PublicSurfacePolicyArchitectureTest::read)
                    .reduce("", String::concat);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}

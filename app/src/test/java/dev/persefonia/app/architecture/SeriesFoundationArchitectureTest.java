package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesFoundationArchitectureTest {
    @Test
    void contentItemDoesNotOwnSeriesForeignKey() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V9__publishing_series.sql"));
        String contentItems = Files.readString(Path.of("src/main/resources/db/migration/V3__publishing.sql"));

        assertThat(migration).doesNotContain("content_items.series_id");
        assertThat(contentItems).doesNotContain("series_id");
    }

    @Test
    void publicSeriesImplementationDoesNotAddForbiddenAdjacentSurfaces() throws Exception {
        assertThat(matches(Path.of("../web-public/src/main"), "@GetMapping(\"/{language}/series\")")).isEmpty();
        assertThat(matches(Path.of("../web-public/src/main"), "/search")).isEmpty();
        assertThat(matches(Path.of("../web-public/src/main"), "/feed")).isEmpty();
        assertThat(matches(Path.of("../web-public/src/main"), "/sitemap")).isEmpty();
        assertThat(matches(Path.of("../web-public/src/main"), "/robots")).isEmpty();
        assertThat(matches(Path.of("src/main/jte/site"), "rel=\"prev\"")).isEmpty();
        assertThat(matches(Path.of("src/main/jte/site"), "rel=\"next\"")).isEmpty();
    }

    @Test
    void discoverySeriesSupportRemainsCurrentOnly() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V10__discovery_series_pages.sql"));

        assertThat(migration)
                .doesNotContain("active")
                .doesNotContain("history")
                .doesNotContain("search_vector");
    }

    @Test
    void noSearchFeedSitemapRobotsRoutesExist() throws Exception {
        String security = Files.readString(Path.of("src/main/java/dev/persefonia/app/security/SecurityConfiguration.java"));

        assertThat(security)
                .doesNotContain("/feed")
                .doesNotContain("/sitemap")
                .doesNotContain("/robots")
                .doesNotContain("/series/**")
                .doesNotContain("/{language}/series/**");
    }

    private static List<String> matches(Path root, String needle) throws Exception {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/build/"))
                    .flatMap(path -> linesContaining(path, needle).stream())
                    .toList();
        }
    }

    private static List<String> linesContaining(Path path, String needle) {
        try {
            return Files.readAllLines(path).stream()
                    .filter(line -> line.contains(needle))
                    .map(line -> path + ": " + line.trim())
                    .toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}

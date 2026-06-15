package dev.persefonia.app.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TranslationGroupPublicNonRegressionTest {
    private static final List<Path> PUBLIC_ROOTS = List.of(
            Path.of("src/main/jte/site"),
            Path.of("../web-public/src/main"));
    private static final List<String> FORBIDDEN_PUBLIC_MARKERS = List.of(
            "hreflang",
            "rel=\"alternate\"",
            "translation-group",
            "translationsection",
            "language switcher");

    @Test
    void publicSurfacesDoNotRenderTranslationLinksOrHreflang() throws IOException {
        List<String> offenders = publicFiles()
                .flatMap(TranslationGroupPublicNonRegressionTest::forbiddenMarkers)
                .toList();

        assertThat(offenders).isEmpty();
    }

    private static Stream<Path> publicFiles() {
        return PUBLIC_ROOTS.stream()
                .filter(Files::exists)
                .flatMap(TranslationGroupPublicNonRegressionTest::walk);
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.toString();
                        return name.endsWith(".jte") || name.endsWith(".java");
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan " + root, exception);
        }
    }

    private static Stream<String> forbiddenMarkers(Path path) {
        try {
            String content = Files.readString(path).toLowerCase(Locale.ROOT);
            return FORBIDDEN_PUBLIC_MARKERS.stream()
                    .filter(content::contains)
                    .map(marker -> path + " contains " + marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}

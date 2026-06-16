package dev.persefonia.app.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TranslationGroupPublicNonRegressionTest {
    private static final Path CONTENT_TEMPLATE = Path.of("src/main/jte/site/content.jte");
    private static final Path TAG_TEMPLATE = Path.of("src/main/jte/site/tag.jte");
    private static final Path SERIES_TEMPLATE = Path.of("src/main/jte/site/series.jte");

    @Test
    void contentDetailTemplateIsTheOnlyPublicTemplateThatRendersTranslationLinksOrHreflang() throws IOException {
        String contentTemplate = normalized(CONTENT_TEMPLATE);
        String tagTemplate = normalized(TAG_TEMPLATE);
        String seriesTemplate = normalized(SERIES_TEMPLATE);

        assertThat(contentTemplate)
                .contains("hreflang")
                .contains("rel=\"alternate\"")
                .contains("public-translations");
        assertThat(tagTemplate)
                .doesNotContain("hreflang")
                .doesNotContain("rel=\"alternate\"")
                .doesNotContain("public-translations");
        assertThat(seriesTemplate)
                .doesNotContain("hreflang")
                .doesNotContain("rel=\"alternate\"")
                .doesNotContain("public-translations");
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}

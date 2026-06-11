package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import org.junit.jupiter.api.Test;

class MarkdownHeadingExtractorTest {
    private final CommonmarkMarkdownParser parser = new CommonmarkMarkdownParser();
    private final MarkdownHeadingExtractor extractor = new MarkdownHeadingExtractor(new HeadingAnchorGenerator());

    @Test
    void extractsAllLevelsInDocumentOrderWithOneBasedPositions() {
        var headings = extract("""
                # One
                ## Two
                ### Three
                #### Four
                ##### Five
                ###### Six
                """);

        assertThat(headings).extracting(heading -> heading.level().value())
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(headings).extracting(heading -> heading.position().value())
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void extractsPlainFormattedTextAndCreatesUniqueDomainValidAnchors() {
        var headings = extract("""
                # Hello *bold* [link](https://example.com) `code`
                ## Intro
                ## Intro
                ## Intro
                """);

        assertThat(headings).extracting(heading -> heading.text().value())
                .containsExactly("Hello bold link code", "Intro", "Intro", "Intro");
        assertThat(headings).extracting(heading -> heading.anchor().value())
                .containsExactly("hello-bold-link-code", "intro", "intro-2", "intro-3");
    }

    @Test
    void rejectsEmptyHeadingText() {
        assertThatThrownBy(() -> extract("#"))
                .isInstanceOf(MarkdownRenderingException.class)
                .hasMessage("Markdown heading text must not be empty");
    }

    private java.util.List<dev.persefonia.contentpublishing.domain.content.RenderedHeading> extract(String markdown) {
        return extractor.extract(parser.parse(MarkdownSource.of(markdown)));
    }
}

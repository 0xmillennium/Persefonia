package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderedHeadingIdInjectorTest {
    private final RenderedHeadingIdInjector injector = new RenderedHeadingIdInjector();

    @Test
    void injectsIdsInDocumentOrderAndSanitizerPreservesThem() {
        String injected = injector.inject(
                "<h1>First</h1><p>body</p><h3>Second</h3>",
                List.of(
                        RenderedHeading.of(1, "First", "first", 1),
                        RenderedHeading.of(3, "Second", "second", 2)));

        assertThat(new JsoupHtmlSanitizer().sanitize(injected))
                .contains("<h1 id=\"first\">First</h1>", "<h3 id=\"second\">Second</h3>");
    }

    @Test
    void rejectsHeadingCountMismatchAndUnexpectedExistingIds() {
        assertThatThrownBy(() -> injector.inject("<h1>Only</h1>", List.of()))
                .isInstanceOf(MarkdownRenderingException.class)
                .hasMessageContaining("count");
        assertThatThrownBy(() -> injector.inject(
                "<h1 id=\"unsafe\">Only</h1>",
                List.of(RenderedHeading.of(1, "Only", "safe", 1))))
                .isInstanceOf(MarkdownRenderingException.class)
                .hasMessageContaining("unexpected id");
    }
}

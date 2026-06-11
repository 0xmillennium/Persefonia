package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CommonmarkMarkdownRenderingServiceTest {
    private static final Instant RENDERED_AT = Instant.parse("2026-06-12T10:00:00Z");

    private final CommonmarkMarkdownRenderingService service = new CommonmarkMarkdownRenderingService();

    @Test
    void rendersMarkdownIntoACompleteSanitizedSnapshot() {
        ContentRenderSnapshot snapshot = service.render(MarkdownSource.of("""
                # Main Heading

                A paragraph with *emphasis* and **strong text**.

                - one
                - two

                ```java
                int value = 1;
                ```
                """), RENDERED_AT);

        assertThat(snapshot.renderedAt()).isEqualTo(RENDERED_AT);
        assertThat(snapshot.rendererVersion().value()).isEqualTo("persefonia-markdown-v1");
        assertThat(snapshot.readingTime().minutes()).isEqualTo(1);
        assertThat(snapshot.containsMermaid()).isFalse();
        assertThat(snapshot.renderedHtml().value())
                .contains("<h1 id=\"main-heading\">Main Heading</h1>", "<p>A paragraph with <em>emphasis</em> and <strong>strong text</strong>.</p>")
                .contains("<ul>", "<li>one</li>", "<pre><code class=\"language-java\">");
        assertThat(snapshot.headings()).singleElement().satisfies(heading -> {
            assertThat(heading.text().value()).isEqualTo("Main Heading");
            assertThat(heading.anchor().value()).isEqualTo("main-heading");
            assertThat(heading.position().value()).isEqualTo(1);
        });
    }

    @Test
    void injectsDeterministicTurkishAndDuplicateHeadingIds() {
        ContentRenderSnapshot snapshot = service.render(MarkdownSource.of("""
                ## İçerik Başlığı
                ## İçerik Başlığı
                """), RENDERED_AT);

        assertThat(snapshot.renderedHtml().value())
                .contains("<h2 id=\"icerik-basligi\">İçerik Başlığı</h2>")
                .contains("<h2 id=\"icerik-basligi-2\">İçerik Başlığı</h2>");
        assertThat(snapshot.headings())
                .extracting(heading -> heading.anchor().value())
                .containsExactly("icerik-basligi", "icerik-basligi-2");
    }

    @Test
    void detectsMermaidWithoutValidatingOrExecutingIt() {
        ContentRenderSnapshot snapshot = service.render(MarkdownSource.of("""
                ```MERMAID title="invalid"
                this is not valid mermaid syntax
                ```
                """), RENDERED_AT);

        assertThat(snapshot.containsMermaid()).isTrue();
        assertThat(snapshot.renderedHtml().value()).contains("class=\"language-MERMAID\"");
    }

    @Test
    void neverReturnsUnsafeRawHtml() {
        ContentRenderSnapshot snapshot = service.render(MarkdownSource.of("""
                Safe text
                <script>alert(1)</script>
                <img src=x onerror=alert(1)>
                <a href="javascript:alert(1)">unsafe</a>
                """), RENDERED_AT);

        String html = snapshot.renderedHtml().value().toLowerCase(Locale.ROOT);
        assertThat(html)
                .doesNotContain("<script", "<img", "onerror=", "javascript:")
                .contains("safe text");
    }
}

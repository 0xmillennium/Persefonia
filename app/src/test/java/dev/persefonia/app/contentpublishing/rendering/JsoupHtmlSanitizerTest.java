package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class JsoupHtmlSanitizerTest {
    private static final String[] FORBIDDEN_FRAGMENTS = {
            "<script", "onerror=", "onclick=", "onload=", "javascript:", "data:", "<iframe",
            "<style", "style=", "<img", "<object", "<embed", "<svg", "<math", "<form", "<input"
    };

    private final JsoupHtmlSanitizer sanitizer = new JsoupHtmlSanitizer();

    @Test
    void removesExecutableTagsAttributesAndUnsafeUrls() {
        String sanitized = sanitizer.sanitize("""
                <script>alert(1)</script>
                <img src=x onerror=alert(1)>
                <a href="javascript:alert(1)">javascript link</a>
                <a href="data:text/html;base64,PHNjcmlwdD5hPC9zY3JpcHQ=">data link</a>
                <div onclick="alert(1)">div text</div>
                <iframe src="https://example.com"></iframe>
                <span style="position:fixed">span text</span>
                <object data="x"></object><embed src="x">
                <svg onload="alert(1)"></svg><math></math>
                <form><input name="x"></form>
                <style>body { display: none }</style>
                """);

        assertThat(sanitized).contains("javascript link", "data link", "div text", "span text");
        assertThat(sanitized.toLowerCase(Locale.ROOT)).doesNotContain(FORBIDDEN_FRAGMENTS);
    }

    @Test
    void preservesExplicitlyAllowedMarkupAndCodeLanguageClass() {
        String sanitized = sanitizer.sanitize("""
                <h1 id="intro">Intro</h1><p>Text <strong>strong</strong> <em>em</em></p>
                <blockquote>quote</blockquote><ul><li>one</li></ul><ol><li>two</li></ol>
                <pre><code class="language-java">int x = 1;</code></pre><hr><br>
                """);

        assertThat(sanitized)
                .contains("<h1 id=\"intro\">", "<p>", "<strong>", "<em>", "<blockquote>", "<ul>", "<ol>")
                .contains("<pre><code class=\"language-java\">int x = 1;</code></pre>")
                .contains("<hr>", "<br>");
    }

    @Test
    void preservesSafeAbsoluteRelativeAndFragmentLinks() {
        String sanitized = sanitizer.sanitize("""
                <a href="http://example.com">http</a>
                <a href="https://example.com" title="safe">https</a>
                <a href="mailto:hello@example.com">mail</a>
                <a href="/relative/path">relative</a>
                <a href="#intro">fragment</a>
                """);

        assertThat(sanitized)
                .contains("href=\"http://example.com\"", "href=\"https://example.com\"", "title=\"safe\"")
                .contains("href=\"mailto:hello@example.com\"", "href=\"/relative/path\"", "href=\"#intro\"");
    }
}

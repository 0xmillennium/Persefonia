package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

final class JsoupHtmlSanitizer {
    private final SanitizedLinkNormalizer linkNormalizer = new SanitizedLinkNormalizer();
    private final Safelist safelist = new Safelist()
            .addTags(
                    "p", "br", "hr", "blockquote", "ul", "ol", "li", "pre", "code",
                    "strong", "em", "a", "h1", "h2", "h3", "h4", "h5", "h6")
            .addAttributes("a", "href", "title")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes("h1", "id")
            .addAttributes("h2", "id")
            .addAttributes("h3", "id")
            .addAttributes("h4", "id")
            .addAttributes("h5", "id")
            .addAttributes("h6", "id")
            .preserveRelativeLinks(true);

    String sanitize(String rawHtml) {
        try {
            Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
            Document document = Jsoup.parseBodyFragment(rawHtml);
            linkNormalizer.removeUnsafeLinks(document);
            return Jsoup.clean(document.body().html(), "", safelist, outputSettings);
        } catch (RuntimeException exception) {
            throw new MarkdownRenderingException("Rendered HTML sanitization failed", exception);
        }
    }
}

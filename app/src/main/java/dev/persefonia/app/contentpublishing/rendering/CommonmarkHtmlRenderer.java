package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import org.commonmark.node.Node;
import org.commonmark.renderer.html.HtmlRenderer;

final class CommonmarkHtmlRenderer {
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .nodeRendererFactory(context -> new RawHtmlOmittingNodeRenderer())
            .build();

    String render(Node document) {
        try {
            return renderer.render(document);
        } catch (RuntimeException exception) {
            throw new MarkdownRenderingException("Markdown HTML rendering failed", exception);
        }
    }
}

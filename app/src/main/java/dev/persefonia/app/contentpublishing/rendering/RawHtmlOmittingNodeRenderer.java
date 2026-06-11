package dev.persefonia.app.contentpublishing.rendering;

import java.util.Set;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;

final class RawHtmlOmittingNodeRenderer implements NodeRenderer {
    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(HtmlBlock.class, HtmlInline.class);
    }

    @Override
    public void render(Node node) {
        // Raw HTML is intentionally omitted before mandatory jsoup sanitization.
    }
}

package dev.persefonia.app.contentpublishing.rendering;

import java.util.Locale;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;

final class MermaidBlockDetector {
    boolean containsMermaid(Node document) {
        MermaidVisitor visitor = new MermaidVisitor();
        document.accept(visitor);
        return visitor.detected;
    }

    private static final class MermaidVisitor extends AbstractVisitor {
        private boolean detected;

        @Override
        public void visit(FencedCodeBlock block) {
            String info = block.getInfo();
            if (info != null) {
                String trimmed = info.trim();
                String firstToken = trimmed.split("\\s+", 2)[0];
                detected = detected || "mermaid".equals(firstToken.toLowerCase(Locale.ROOT));
            }
        }
    }
}

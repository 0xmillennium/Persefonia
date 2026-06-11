package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

final class MarkdownHeadingExtractor {
    private final HeadingAnchorGenerator anchorGenerator;

    MarkdownHeadingExtractor(HeadingAnchorGenerator anchorGenerator) {
        this.anchorGenerator = anchorGenerator;
    }

    List<RenderedHeading> extract(Node document) {
        List<HeadingData> headings = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                headings.add(new HeadingData(heading.getLevel(), extractText(heading)));
            }
        });

        List<String> texts = headings.stream().map(HeadingData::text).toList();
        List<HeadingAnchor> anchors = anchorGenerator.generateUniqueAnchors(texts);
        List<RenderedHeading> renderedHeadings = new ArrayList<>(headings.size());
        for (int index = 0; index < headings.size(); index++) {
            HeadingData heading = headings.get(index);
            renderedHeadings.add(RenderedHeading.of(
                    heading.level(), heading.text(), anchors.get(index).value(), index + 1));
        }
        return List.copyOf(renderedHeadings);
    }

    private String extractText(Heading heading) {
        StringBuilder text = new StringBuilder();
        heading.accept(new AbstractVisitor() {
            @Override
            public void visit(Text node) {
                text.append(node.getLiteral());
            }

            @Override
            public void visit(Code node) {
                text.append(node.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak node) {
                text.append(' ');
            }

            @Override
            public void visit(HardLineBreak node) {
                text.append(' ');
            }
        });
        String collapsed = text.toString().replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            throw new MarkdownRenderingException("Markdown heading text must not be empty");
        }
        return collapsed;
    }

    private record HeadingData(int level, String text) {
    }
}

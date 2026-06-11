package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

final class CommonmarkMarkdownParser {
    private final Parser parser = Parser.builder().build();

    Node parse(MarkdownSource source) {
        try {
            return parser.parse(source.value());
        } catch (RuntimeException exception) {
            throw new MarkdownRenderingException("Markdown parsing failed", exception);
        }
    }
}

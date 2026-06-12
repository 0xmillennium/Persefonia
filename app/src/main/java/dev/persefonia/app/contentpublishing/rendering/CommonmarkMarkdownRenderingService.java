package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import java.time.Instant;
import org.commonmark.node.Node;

public final class CommonmarkMarkdownRenderingService implements MarkdownRenderingService {
    private final CommonmarkMarkdownParser parser;
    private final MarkdownHeadingExtractor headingExtractor;
    private final MermaidBlockDetector mermaidBlockDetector;
    private final CommonmarkHtmlRenderer htmlRenderer;
    private final JsoupHtmlSanitizer htmlSanitizer;
    private final RenderedHeadingIdInjector headingIdInjector;
    private final MarkdownReadingTimeCalculator readingTimeCalculator;
    private final MarkdownRendererVersion rendererVersion;

    public CommonmarkMarkdownRenderingService() {
        this(
                new CommonmarkMarkdownParser(),
                new MarkdownHeadingExtractor(new HeadingAnchorGenerator()),
                new MermaidBlockDetector(),
                new CommonmarkHtmlRenderer(),
                new JsoupHtmlSanitizer(),
                new RenderedHeadingIdInjector(),
                new MarkdownReadingTimeCalculator(),
                new MarkdownRendererVersion());
    }

    CommonmarkMarkdownRenderingService(
            CommonmarkMarkdownParser parser,
            MarkdownHeadingExtractor headingExtractor,
            MermaidBlockDetector mermaidBlockDetector,
            CommonmarkHtmlRenderer htmlRenderer,
            JsoupHtmlSanitizer htmlSanitizer,
            RenderedHeadingIdInjector headingIdInjector,
            MarkdownReadingTimeCalculator readingTimeCalculator,
            MarkdownRendererVersion rendererVersion) {
        this.parser = parser;
        this.headingExtractor = headingExtractor;
        this.mermaidBlockDetector = mermaidBlockDetector;
        this.htmlRenderer = htmlRenderer;
        this.htmlSanitizer = htmlSanitizer;
        this.headingIdInjector = headingIdInjector;
        this.readingTimeCalculator = readingTimeCalculator;
        this.rendererVersion = rendererVersion;
    }

    @Override
    public ContentRenderSnapshot render(MarkdownSource source, Instant renderedAt) {
        if (source == null || renderedAt == null) {
            throw new MarkdownRenderingException("Markdown source and renderedAt are required");
        }
        try {
            Node document = parser.parse(source);
            var headings = headingExtractor.extract(document);
            boolean containsMermaid = mermaidBlockDetector.containsMermaid(document);
            String rawHtml = htmlRenderer.render(document);
            String sanitizedHtml = htmlSanitizer.sanitize(rawHtml);
            String htmlWithHeadingIds = headingIdInjector.inject(sanitizedHtml, headings);
            String finalHtml = htmlSanitizer.sanitize(htmlWithHeadingIds);
            return ContentRenderSnapshot.of(
                    RenderedHtml.sanitized(finalHtml),
                    renderedAt,
                    rendererVersion.current(),
                    readingTimeCalculator.calculate(source),
                    containsMermaid,
                    headings);
        } catch (MarkdownRenderingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarkdownRenderingException("Markdown rendering failed", exception);
        }
    }
}

package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import java.time.Instant;
import java.util.List;

public final class FakeMarkdownRenderingService implements MarkdownRenderingService {
    private int renderCount;

    @Override
    public ContentRenderSnapshot render(MarkdownSource source, Instant renderedAt) {
        renderCount++;
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<h1 id=\"rendered\">Rendered</h1>"),
                renderedAt,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(1),
                true,
                List.of(RenderedHeading.of(1, "Rendered", "rendered", 1)));
    }

    public int renderCount() {
        return renderCount;
    }
}

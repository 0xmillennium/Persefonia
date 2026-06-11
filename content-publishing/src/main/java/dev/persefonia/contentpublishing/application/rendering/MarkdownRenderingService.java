package dev.persefonia.contentpublishing.application.rendering;

import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import java.time.Instant;

public interface MarkdownRenderingService {
    ContentRenderSnapshot render(MarkdownSource source, Instant renderedAt);
}

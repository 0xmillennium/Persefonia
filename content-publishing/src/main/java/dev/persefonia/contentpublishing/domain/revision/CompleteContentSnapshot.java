package dev.persefonia.contentpublishing.domain.revision;

import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.util.Objects;
import java.util.Optional;

public final class CompleteContentSnapshot {
    private final Title title;
    private final Slug slug;
    private final Summary summary;
    private final MarkdownSource markdownSource;
    private final RenderedHtml renderedHtml;
    private final RevisionMetadata metadata;

    public CompleteContentSnapshot(
            Title title,
            Slug slug,
            Summary summary,
            MarkdownSource markdownSource,
            RenderedHtml renderedHtml,
            RevisionMetadata metadata) {
        this.title = Objects.requireNonNull(title, "title");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.markdownSource = Objects.requireNonNull(markdownSource, "markdownSource");
        this.renderedHtml = renderedHtml;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public static CompleteContentSnapshot of(
            Title title,
            Slug slug,
            Summary summary,
            MarkdownSource markdownSource,
            RenderedHtml renderedHtml,
            RevisionMetadata metadata) {
        return new CompleteContentSnapshot(title, slug, summary, markdownSource, renderedHtml, metadata);
    }

    public Title title() {
        return title;
    }

    public Slug slug() {
        return slug;
    }

    public Summary summary() {
        return summary;
    }

    public MarkdownSource markdownSource() {
        return markdownSource;
    }

    public Optional<RenderedHtml> renderedHtml() {
        return Optional.ofNullable(renderedHtml);
    }

    public RevisionMetadata metadata() {
        return metadata;
    }
}

package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PublicContentPageResult(
        ContentId contentId,
        ContentType type,
        ContentLanguage language,
        ContentVisibility visibility,
        Slug slug,
        Title title,
        Summary summary,
        CanonicalPath canonicalPath,
        Optional<SeoTitle> seoTitle,
        Optional<SeoDescription> seoDescription,
        Optional<OpenGraphTitle> openGraphTitle,
        Optional<OpenGraphDescription> openGraphDescription,
        Instant publishedAt,
        Instant updatedAt,
        RenderedHtml renderedHtml,
        RendererVersion rendererVersion,
        ReadingTime readingTime,
        boolean containsMermaid,
        List<PublicContentHeadingResult> headings) {
    public PublicContentPageResult {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(canonicalPath, "canonicalPath");
        seoTitle = Objects.requireNonNull(seoTitle, "seoTitle");
        seoDescription = Objects.requireNonNull(seoDescription, "seoDescription");
        openGraphTitle = Objects.requireNonNull(openGraphTitle, "openGraphTitle");
        openGraphDescription = Objects.requireNonNull(openGraphDescription, "openGraphDescription");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(renderedHtml, "renderedHtml");
        Objects.requireNonNull(rendererVersion, "rendererVersion");
        Objects.requireNonNull(readingTime, "readingTime");
        headings = List.copyOf(Objects.requireNonNull(headings, "headings"));
    }
}

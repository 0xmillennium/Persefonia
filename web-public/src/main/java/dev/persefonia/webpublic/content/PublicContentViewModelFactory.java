package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.webpublic.FrontendAssetResolver;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class PublicContentViewModelFactory {
    private static final String MAIN_FRONTEND_ENTRY = "src/main.ts";
    private static final String MERMAID_FRONTEND_ENTRY = "src/mermaid-loader.ts";
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final FrontendAssetResolver assetResolver;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;

    public PublicContentViewModelFactory(
            FrontendAssetResolver assetResolver,
            PublicCanonicalUrlFactory canonicalUrlFactory) {
        this.assetResolver = assetResolver;
        this.canonicalUrlFactory = canonicalUrlFactory;
    }

    public PublicContentPage contentPage(PublicContentPageResult page) {
        String title = page.title().value();
        String summary = page.summary().value();
        String seoTitle = page.seoTitle().map(value -> value.value()).orElse(title);
        String seoDescription = page.seoDescription().map(value -> value.value()).orElse(summary);
        String openGraphTitle = page.openGraphTitle().map(value -> value.value()).orElse(seoTitle);
        String openGraphDescription = page.openGraphDescription().map(value -> value.value()).orElse(seoDescription);
        String canonicalUrl = canonicalUrlFactory.canonicalUrl(page.canonicalPath().value());

        return new PublicContentPage(
                title,
                title,
                summary,
                typeLabel(page.type()),
                htmlLanguage(page.language()),
                page.canonicalPath().value(),
                seoTitle,
                seoDescription,
                canonicalUrl,
                openGraphTitle,
                openGraphDescription,
                canonicalUrl,
                openGraphType(page.type()),
                page.publishedAt().toString(),
                page.updatedAt().toString(),
                DISPLAY_DATE_FORMATTER.format(page.publishedAt()),
                readingTimeLabel(page.readingTime().minutes()),
                page.renderedHtml().value(),
                page.containsMermaid(),
                page.visibility() == ContentVisibility.UNLISTED,
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY),
                mermaidScriptPath(page.containsMermaid()),
                page.headings().stream().map(this::heading).toList());
    }

    public PublicNotFoundPage notFoundPage() {
        return new PublicNotFoundPage(
                "Not found",
                "The page you requested was not found.",
                true,
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY));
    }

    private PublicContentHeadingView heading(PublicContentHeadingResult heading) {
        return new PublicContentHeadingView(
                heading.level().value(),
                heading.text().value(),
                heading.anchor().value(),
                heading.position().value());
    }

    private static String typeLabel(ContentType type) {
        return switch (type) {
            case ARTICLE -> "Article";
            case NOTE -> "Note";
            case RESEARCH -> "Research";
            case PAGE -> "Page";
        };
    }

    private static String htmlLanguage(ContentLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    private static String openGraphType(ContentType type) {
        return switch (type) {
            case ARTICLE, NOTE, RESEARCH -> "article";
            case PAGE -> "website";
        };
    }

    private static String readingTimeLabel(int minutes) {
        return minutes == 1 ? "1 min read" : minutes + " min read";
    }

    private Optional<String> mermaidScriptPath(boolean containsMermaid) {
        return containsMermaid
                ? Optional.of(assetResolver.scriptPath(MERMAID_FRONTEND_ENTRY))
                : Optional.empty();
    }
}

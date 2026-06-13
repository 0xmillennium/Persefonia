package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class PublicContentViewModelFactory {
    public PublicContentPage contentPage(PublicContentPageResult page) {
        return new PublicContentPage(
                page.title().value(),
                page.summary().value(),
                page.publishedAt(),
                page.readingTime().minutes(),
                typeLabel(page.type()),
                languageLabel(page.language()),
                page.canonicalPath().value(),
                page.seoTitle().map(seoTitle -> seoTitle.value()),
                page.seoDescription().map(seoDescription -> seoDescription.value()),
                page.openGraphTitle().map(openGraphTitle -> openGraphTitle.value()),
                page.openGraphDescription().map(openGraphDescription -> openGraphDescription.value()),
                page.renderedHtml().value(),
                page.containsMermaid(),
                page.headings().stream().map(this::heading).toList());
    }

    public PublicNotFoundPage notFoundPage() {
        return new PublicNotFoundPage("Not found", "The page you requested was not found.", true);
    }

    private PublicContentHeadingView heading(PublicContentHeadingResult heading) {
        return new PublicContentHeadingView(
                heading.level().value(),
                heading.text().value(),
                heading.anchor().value());
    }

    private static String typeLabel(ContentType type) {
        return switch (type) {
            case ARTICLE -> "Article";
            case NOTE -> "Note";
            case RESEARCH -> "Research";
            case PAGE -> "Page";
        };
    }

    private static String languageLabel(ContentLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }
}

package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentValidationException;
import dev.persefonia.contentpublishing.domain.content.Slug;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class PublicContentRouteParser {
    public Optional<PublicContentRouteQuery> parse(String language, String collection, String slug) {
        Optional<ContentLanguage> parsedLanguage = parseLanguage(language);
        Optional<ContentType> parsedType = parseCollection(collection);
        if (parsedLanguage.isEmpty() || parsedType.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PublicContentRouteQuery(parsedType.get(), parsedLanguage.get(), Slug.of(slug)));
        } catch (ContentValidationException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static Optional<ContentLanguage> parseLanguage(String language) {
        if (language == null) {
            return Optional.empty();
        }
        return switch (language) {
            case "tr" -> Optional.of(ContentLanguage.TR);
            case "en" -> Optional.of(ContentLanguage.EN);
            default -> Optional.empty();
        };
    }

    private static Optional<ContentType> parseCollection(String collection) {
        if (collection == null) {
            return Optional.empty();
        }
        return switch (collection) {
            case "articles" -> Optional.of(ContentType.ARTICLE);
            case "notes" -> Optional.of(ContentType.NOTE);
            case "research" -> Optional.of(ContentType.RESEARCH);
            case "pages" -> Optional.of(ContentType.PAGE);
            default -> Optional.empty();
        };
    }
}

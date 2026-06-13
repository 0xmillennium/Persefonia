package dev.persefonia.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import org.junit.jupiter.api.Test;

class PublicContentRouteParserTest {
    private final PublicContentRouteParser parser = new PublicContentRouteParser();

    @Test
    void mapsSupportedLanguages() {
        assertThat(parser.parse("tr", "articles", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.language()).isEqualTo(ContentLanguage.TR));
        assertThat(parser.parse("en", "articles", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.language()).isEqualTo(ContentLanguage.EN));
    }

    @Test
    void mapsSupportedCollections() {
        assertThat(parser.parse("en", "articles", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.type()).isEqualTo(ContentType.ARTICLE));
        assertThat(parser.parse("en", "notes", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.type()).isEqualTo(ContentType.NOTE));
        assertThat(parser.parse("en", "research", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.type()).isEqualTo(ContentType.RESEARCH));
        assertThat(parser.parse("en", "pages", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.type()).isEqualTo(ContentType.PAGE));
    }

    @Test
    void rejectsInvalidLanguageCollectionAndSlug() {
        assertThat(parser.parse("de", "articles", "valid-slug")).isEmpty();
        assertThat(parser.parse("en", "essays", "valid-slug")).isEmpty();
        assertThat(parser.parse("en", "articles", "Invalid-Slug")).isEmpty();
        assertThat(parser.parse("en", "articles", "with_underscore")).isEmpty();
    }

    @Test
    void validSlugProducesRouteQuerySlug() {
        assertThat(parser.parse("en", "articles", "valid-slug"))
                .hasValueSatisfying(query -> assertThat(query.slug().value()).isEqualTo("valid-slug"));
    }
}

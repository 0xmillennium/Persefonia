package dev.persefonia.webpublic.tags;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import org.junit.jupiter.api.Test;

class PublicTagRouteParserTest {
    private final PublicTagRouteParser parser = new PublicTagRouteParser();

    @Test
    void parsesTurkishAndEnglishTagRoutes() {
        assertThat(parser.parse("tr", "spring")).contains(new PublicTagRoute(DiscoveryLanguage.TR, "spring"));
        assertThat(parser.parse("en", "spring-framework"))
                .contains(new PublicTagRoute(DiscoveryLanguage.EN, "spring-framework"));
    }

    @Test
    void rejectsInvalidLanguageAndSlug() {
        assertThat(parser.parse("de", "spring")).isEmpty();
        assertThat(parser.parse("en", "Spring")).isEmpty();
        assertThat(parser.parse("en", "")).isEmpty();
        assertThat(parser.parse("en", "spring/extra")).isEmpty();
    }
}

package dev.persefonia.webpublic.series;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import org.junit.jupiter.api.Test;

class PublicSeriesRouteParserTest {
    private final PublicSeriesRouteParser parser = new PublicSeriesRouteParser();

    @Test
    void parsesTurkishAndEnglishSeriesRoutes() {
        assertThat(parser.parse("tr", "spring-boot-notlari"))
                .contains(new PublicSeriesRoute(DiscoveryLanguage.TR, "spring-boot-notlari"));
        assertThat(parser.parse("en", "spring-boot-notes"))
                .contains(new PublicSeriesRoute(DiscoveryLanguage.EN, "spring-boot-notes"));
    }

    @Test
    void rejectsInvalidLanguageAndSlug() {
        assertThat(parser.parse("de", "spring")).isEmpty();
        assertThat(parser.parse("en", "Spring")).isEmpty();
        assertThat(parser.parse("en", "")).isEmpty();
        assertThat(parser.parse("en", "spring/extra")).isEmpty();
    }

    @Test
    void rejectsSeriesIndexAndTrailingSlashInputs() {
        assertThat(parser.parse("en", null)).isEmpty();
        assertThat(parser.parse("en", "spring-")).isEmpty();
    }
}

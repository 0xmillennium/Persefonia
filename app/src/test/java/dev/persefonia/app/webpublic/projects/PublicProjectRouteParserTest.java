package dev.persefonia.app.webpublic.projects;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.webpublic.projects.PublicProjectRouteParser;
import org.junit.jupiter.api.Test;

class PublicProjectRouteParserTest {
    private final PublicProjectRouteParser parser = new PublicProjectRouteParser();

    @Test
    void parsesValidProjectRoutes() {
        assertThat(parser.parse("tr", "iyi-proje"))
                .get()
                .satisfies(route -> {
                    assertThat(route.language()).isEqualTo(DiscoveryLanguage.TR);
                    assertThat(route.publicPath()).isEqualTo("/tr/projects/iyi-proje");
                });
        assertThat(parser.parse("en", "good-project"))
                .get()
                .satisfies(route -> {
                    assertThat(route.language()).isEqualTo(DiscoveryLanguage.EN);
                    assertThat(route.publicPath()).isEqualTo("/en/projects/good-project");
                });
    }

    @Test
    void rejectsInvalidProjectRoutes() {
        assertThat(parser.parse("de", "good-project")).isEmpty();
        assertThat(parser.parse("en", "BadProject")).isEmpty();
        assertThat(parser.parse("en", "bad/project")).isEmpty();
        assertThat(parser.parse("en", "")).isEmpty();
    }
}

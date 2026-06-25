package dev.persefonia.webpublic.robots;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import org.junit.jupiter.api.Test;

class PublicRobotsDocumentServiceTest {
    private final PublicRobotsDocumentService service =
            new PublicRobotsDocumentService(new PublicCanonicalUrlFactory("https://example.test"));

    @Test
    void rendersUserAgentRequiredDisallowsAndAbsoluteSitemap() {
        String body = service.render();

        assertThat(body).startsWith("User-agent: *\n");
        assertThat(body).contains("Disallow: /admin\n");
        assertThat(body).contains("Disallow: /actuator\n");
        assertThat(body).contains("Disallow: /oauth2\n");
        assertThat(body).contains("Disallow: /login\n");
        assertThat(body).contains("Disallow: /logout\n");
        assertThat(body).contains("Disallow: /preview\n");
        assertThat(body).contains("Disallow: /search\n");
        assertThat(body).contains("Disallow: /cv/download\n");
        assertThat(body).contains("Disallow: /cv/*/download\n");
        assertThat(body).contains("Sitemap: https://example.test/sitemap.xml\n");
    }

    @Test
    void doesNotRevealInternalHostsOrImplementationDetails() {
        String body = service.render();

        assertThat(body).doesNotContain("localhost");
        assertThat(body).doesNotContain("DocumentService");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("#");
    }
}

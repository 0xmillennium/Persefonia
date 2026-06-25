package dev.persefonia.webpublic.robots;

import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Renders the advisory {@code robots.txt} document.
 *
 * <p>The {@code Sitemap} line uses the configured canonical base URL, never request host data. The
 * document is advisory only and must not reveal secrets, internal hosts, or implementation details.
 */
@Service
public final class PublicRobotsDocumentService {
    static final List<String> DISALLOW = List.of(
            "/admin",
            "/actuator",
            "/oauth2",
            "/login",
            "/logout",
            "/preview",
            "/search",
            "/cv/download",
            "/cv/*/download");

    private final PublicCanonicalUrlFactory canonicalUrlFactory;

    public PublicRobotsDocumentService(PublicCanonicalUrlFactory canonicalUrlFactory) {
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    public String render() {
        StringBuilder body = new StringBuilder(256);
        body.append("User-agent: *\n");
        for (String disallow : DISALLOW) {
            body.append("Disallow: ").append(disallow).append('\n');
        }
        body.append("Sitemap: ").append(canonicalUrlFactory.canonicalUrl("/sitemap.xml")).append('\n');
        return body.toString();
    }
}

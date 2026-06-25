package dev.persefonia.webpublic.sitemap;

import dev.persefonia.discovery.application.index.PublicIndexLimits;
import dev.persefonia.discovery.application.index.PublicSitemapEntry;
import dev.persefonia.discovery.application.index.PublicSitemapIndexQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Builds the public {@code sitemap.xml} document from explicit static routes and Discovery sitemap
 * eligibility, rendering absolute, canonical, deduplicated, XML-escaped {@code <loc>} values.
 *
 * <p>All absolute URLs are derived from the configured canonical base URL plus a safe public path.
 * Request host data is never consulted. Dynamic absolute canonical URLs from Discovery are not
 * trusted for {@code <loc>}; only the resource public path is reused.
 */
@Service
public final class PublicSitemapDocumentService {
    private static final DateTimeFormatter LASTMOD = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Public path prefixes that must never appear in the sitemap, regardless of source.
     */
    private static final List<String> FORBIDDEN_PATH_PREFIXES = List.of(
            "/admin",
            "/actuator",
            "/oauth2",
            "/login",
            "/logout",
            "/preview",
            "/search",
            "/media",
            "/sitemap",
            "/robots",
            "/feed",
            "/rss",
            "/atom");

    private final PublicSitemapIndexQueryService sitemapIndex;
    private final PublicSitemapStaticRouteProvider staticRoutes;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;

    public PublicSitemapDocumentService(
            PublicSitemapIndexQueryService sitemapIndex,
            PublicSitemapStaticRouteProvider staticRoutes,
            PublicCanonicalUrlFactory canonicalUrlFactory) {
        this.sitemapIndex = Objects.requireNonNull(sitemapIndex, "sitemapIndex");
        this.staticRoutes = Objects.requireNonNull(staticRoutes, "staticRoutes");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
    }

    public String renderXml() {
        Map<String, LocalDate> entries = new LinkedHashMap<>();

        for (String path : staticRoutes.staticPaths()) {
            if (isSafePublicPath(path)) {
                entries.putIfAbsent(canonicalUrlFactory.canonicalUrl(path), null);
            }
        }

        for (PublicSitemapEntry entry : sitemapIndex.findSitemapEntries(PublicIndexLimits.MAX_SITEMAP_LIMIT)) {
            String path = entry.publicUrl();
            if (!isSafePublicPath(path)) {
                continue;
            }
            String loc = canonicalUrlFactory.canonicalUrl(path);
            LocalDate lastModified = entry.lastModifiedAt() == null
                    ? null
                    : LocalDate.ofInstant(entry.lastModifiedAt(), ZoneOffset.UTC);
            entries.putIfAbsent(loc, lastModified);
        }

        return render(entries);
    }

    private static String render(Map<String, LocalDate> entries) {
        StringBuilder xml = new StringBuilder(256 + entries.size() * 96);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Map.Entry<String, LocalDate> entry : entries.entrySet()) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(entry.getKey())).append("</loc>\n");
            if (entry.getValue() != null) {
                xml.append("    <lastmod>").append(LASTMOD.format(entry.getValue())).append("</lastmod>\n");
            }
            xml.append("  </url>\n");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    static boolean isSafePublicPath(String path) {
        if (path == null || !path.startsWith("/")) {
            return false;
        }
        if (path.contains("://")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                return false;
            }
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : FORBIDDEN_PATH_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/") || lower.startsWith(prefix + ".")) {
                return false;
            }
        }
        // CV downloads (/cv/download, /cv/{language}/download) are never sitemap entries.
        if (lower.equals("/cv/download") || (lower.startsWith("/cv/") && lower.endsWith("/download"))) {
            return false;
        }
        return true;
    }

    static String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}

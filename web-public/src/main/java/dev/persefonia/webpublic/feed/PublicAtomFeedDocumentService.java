package dev.persefonia.webpublic.feed;

import dev.persefonia.discovery.application.index.PublicFeedEntry;
import dev.persefonia.discovery.application.index.PublicFeedIndexQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the public {@code /feed.xml} Atom 1.0 document from Discovery feed eligibility.
 *
 * <p>The feed is summary-only. Entry and feed URLs are derived from the configured canonical base URL
 * plus the resource public path; request host data is never consulted. Dynamic absolute URLs from
 * Discovery are not trusted for syndication. Even though {@link PublicFeedIndexQueryService} already
 * filters eligibility, a defensive path guard excludes unsafe route families and the feed self route,
 * and entries are deduplicated by their final absolute URL.
 *
 * <p>No {@code <content>}, media enclosures, RSS elements, or raw HTML are emitted.
 */
@Service
public final class PublicAtomFeedDocumentService {
    static final String FEED_PATH = "/feed.xml";
    static final int DEFAULT_FEED_LIMIT = 20;

    /**
     * Deterministic {@code <updated>} value used only when the feed has no entries, avoiding any
     * dependence on the wall clock so the empty feed renders identically and remains testable.
     */
    static final Instant EMPTY_FEED_UPDATED = Instant.parse("2024-01-01T00:00:00Z");

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_INSTANT;

    /**
     * Public path prefixes that must never appear as feed entries, regardless of source.
     */
    private static final List<String> FORBIDDEN_PATH_PREFIXES = List.of(
            "/search",
            "/sitemap.xml",
            "/robots.txt",
            "/feed.xml",
            "/rss.xml",
            "/atom.xml",
            "/cv",
            "/media",
            "/admin",
            "/oauth2",
            "/login",
            "/logout",
            "/actuator",
            "/preview",
            "/tags",
            "/series");

    private final PublicFeedIndexQueryService feedIndex;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final String feedTitle;
    private final String feedSubtitle;

    public PublicAtomFeedDocumentService(
            PublicFeedIndexQueryService feedIndex,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            @Value("${site.feed.title:0xmillennium}") String feedTitle,
            @Value("${site.feed.subtitle:Latest articles, notes, and research.}") String feedSubtitle) {
        this.feedIndex = Objects.requireNonNull(feedIndex, "feedIndex");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.feedTitle = requireText(feedTitle, "feedTitle");
        this.feedSubtitle = requireText(feedSubtitle, "feedSubtitle");
    }

    public String renderXml() {
        Map<String, PublicFeedEntry> byAbsoluteUrl = new LinkedHashMap<>();
        Instant newestUpdated = null;

        for (PublicFeedEntry entry : feedIndex.findLatestFeedEntries(DEFAULT_FEED_LIMIT)) {
            String path = entry.publicUrl();
            if (!isSafePublicPath(path) || isProjectEntry(entry)) {
                continue;
            }
            String absoluteUrl = canonicalUrlFactory.canonicalUrl(path);
            if (byAbsoluteUrl.putIfAbsent(absoluteUrl, entry) != null) {
                continue;
            }
            if (newestUpdated == null || entry.updatedAt().isAfter(newestUpdated)) {
                newestUpdated = entry.updatedAt();
            }
        }

        Instant feedUpdated = newestUpdated == null ? EMPTY_FEED_UPDATED : newestUpdated;
        return render(byAbsoluteUrl, feedUpdated);
    }

    private static boolean isProjectEntry(PublicFeedEntry entry) {
        return "PROJECT".equalsIgnoreCase(entry.sourceType());
    }

    private String render(Map<String, PublicFeedEntry> entries, Instant feedUpdated) {
        String selfUrl = canonicalUrlFactory.canonicalUrl(FEED_PATH);
        String alternateUrl = canonicalUrlFactory.canonicalUrl("/");

        StringBuilder xml = new StringBuilder(512 + entries.size() * 256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">\n");
        xml.append("  <id>").append(escapeXml(selfUrl)).append("</id>\n");
        xml.append("  <title>").append(escapeXml(feedTitle)).append("</title>\n");
        xml.append("  <subtitle>").append(escapeXml(feedSubtitle)).append("</subtitle>\n");
        xml.append("  <updated>").append(RFC3339.format(feedUpdated)).append("</updated>\n");
        xml.append("  <link rel=\"self\" href=\"").append(escapeXml(selfUrl)).append("\"/>\n");
        xml.append("  <link rel=\"alternate\" href=\"").append(escapeXml(alternateUrl)).append("\"/>\n");

        for (Map.Entry<String, PublicFeedEntry> mapping : entries.entrySet()) {
            String absoluteUrl = mapping.getKey();
            PublicFeedEntry entry = mapping.getValue();
            xml.append("  <entry>\n");
            xml.append("    <id>").append(escapeXml(absoluteUrl)).append("</id>\n");
            xml.append("    <title>").append(escapeXml(entry.title())).append("</title>\n");
            xml.append("    <link rel=\"alternate\" href=\"").append(escapeXml(absoluteUrl)).append("\"/>\n");
            xml.append("    <updated>").append(RFC3339.format(entry.updatedAt())).append("</updated>\n");
            xml.append("    <published>").append(RFC3339.format(entry.publishedAt())).append("</published>\n");
            xml.append("    <summary>").append(escapeXml(entry.summary())).append("</summary>\n");
            xml.append("  </entry>\n");
        }

        xml.append("</feed>\n");
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
        String lower = path.toLowerCase(Locale.ROOT);
        for (String prefix : FORBIDDEN_PATH_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/") || lower.startsWith(prefix + ".")) {
                return false;
            }
        }
        // Language-prefixed tag and series route families are feed-ineligible.
        if (lower.matches("/(tr|en)/(tags|series)(/.*)?")) {
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

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

package dev.persefonia.app.contentpublishing.rendering;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class SanitizedLinkNormalizer {
    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");
    private static final Pattern CONTROL_OR_WHITESPACE = Pattern.compile("[\\p{Cntrl}\\s]+");
    private static final Set<String> SAFE_SCHEMES = Set.of("http", "https", "mailto");

    void removeUnsafeLinks(Document document) {
        for (Element link : document.select("a[href]")) {
            if (!isSafe(link.attr("href"))) {
                link.removeAttr("href");
            }
        }
    }

    private boolean isSafe(String href) {
        String compact = CONTROL_OR_WHITESPACE.matcher(href).replaceAll("");
        if (compact.isEmpty() || compact.startsWith("//") || compact.startsWith("\\\\")) {
            return false;
        }
        var matcher = SCHEME.matcher(compact);
        return !matcher.find() || SAFE_SCHEMES.contains(matcher.group(1).toLowerCase(Locale.ROOT));
    }
}

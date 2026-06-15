package dev.persefonia.webpublic.tags;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class PublicTagRouteParser {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public Optional<PublicTagRoute> parse(String language, String slug) {
        DiscoveryLanguage parsedLanguage = switch (language == null ? "" : language) {
            case "tr" -> DiscoveryLanguage.TR;
            case "en" -> DiscoveryLanguage.EN;
            default -> null;
        };
        if (parsedLanguage == null || slug == null || !SLUG.matcher(slug).matches()) {
            return Optional.empty();
        }
        return Optional.of(new PublicTagRoute(parsedLanguage, slug));
    }
}

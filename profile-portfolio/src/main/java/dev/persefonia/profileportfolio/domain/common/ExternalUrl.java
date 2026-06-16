package dev.persefonia.profileportfolio.domain.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

public record ExternalUrl(String value) {
    public ExternalUrl {
        Objects.requireNonNull(value, "value");
        if (containsControlCharacter(value)) {
            throw new PortfolioValidationException("external URL must not contain control characters");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new PortfolioValidationException("external URL must not be blank");
        }

        URI uri = parse(normalized);
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new PortfolioValidationException("external URL must include a scheme");
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("https") && !normalizedScheme.equals("http")) {
            throw new PortfolioValidationException("external URL scheme must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new PortfolioValidationException("external URL must include a host");
        }
        value = normalized;
    }

    public static ExternalUrl of(String value) {
        return new ExternalUrl(value);
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw new PortfolioValidationException("external URL must be syntactically valid");
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}

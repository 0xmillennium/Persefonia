package dev.persefonia.contentpublishing.application.query;

import java.util.Objects;

public record PublicHreflangLink(
        String languageCode,
        String href) {
    public PublicHreflangLink {
        languageCode = Objects.requireNonNull(languageCode, "languageCode");
        href = Objects.requireNonNull(href, "href");
    }
}

package dev.persefonia.webpublic.content;

import java.util.Objects;

public record PublicContentHreflangLinkView(
        String languageCode,
        String href) {
    public PublicContentHreflangLinkView {
        languageCode = Objects.requireNonNull(languageCode, "languageCode");
        href = Objects.requireNonNull(href, "href");
    }
}

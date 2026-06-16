package dev.persefonia.contentpublishing.application.query;

import java.util.List;
import java.util.Objects;

public record PublicTranslationLinkSet(
        boolean renderVisibleLinks,
        boolean renderHreflang,
        List<PublicTranslationLink> visibleLinks,
        List<PublicHreflangLink> hreflangLinks) {
    private static final PublicTranslationLinkSet EMPTY =
            new PublicTranslationLinkSet(false, false, List.of(), List.of());

    public PublicTranslationLinkSet {
        visibleLinks = List.copyOf(Objects.requireNonNull(visibleLinks, "visibleLinks"));
        hreflangLinks = List.copyOf(Objects.requireNonNull(hreflangLinks, "hreflangLinks"));
        if (renderVisibleLinks && visibleLinks.isEmpty()) {
            throw new IllegalArgumentException("visible links must be present when rendering visible links");
        }
        if (renderHreflang && hreflangLinks.size() < 2) {
            throw new IllegalArgumentException("hreflang requires self and at least one alternate");
        }
    }

    public static PublicTranslationLinkSet empty() {
        return EMPTY;
    }

    public static PublicTranslationLinkSet withAlternates(
            List<PublicTranslationLink> visibleLinks,
            List<PublicHreflangLink> hreflangLinks) {
        return new PublicTranslationLinkSet(true, true, visibleLinks, hreflangLinks);
    }
}

package dev.persefonia.webpublic.tags;

import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.taxonomy.application.query.PublicTagView;
import dev.persefonia.webpublic.FrontendAssetResolver;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class PublicTagPageViewModelFactory {
    private static final String MAIN_FRONTEND_ENTRY = "src/main.ts";
    private final FrontendAssetResolver assetResolver;

    public PublicTagPageViewModelFactory(FrontendAssetResolver assetResolver) {
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
    }

    public PublicTagPage page(
            PublicTagView tag,
            DiscoveryLanguage language,
            String publicUrl,
            String canonicalUrl,
            List<PublicTaggedContentItem> items) {
        return new PublicTagPage(
                tag.name(),
                tag.description(),
                tag.status(),
                language.name().toLowerCase(Locale.ROOT),
                publicUrl,
                canonicalUrl,
                true,
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY),
                items.stream().map(PublicTagPageViewModelFactory::item).toList());
    }

    private static PublicTagContentItemView item(PublicTaggedContentItem item) {
        return new PublicTagContentItemView(
                item.title(),
                item.summary(),
                item.publicUrl(),
                item.canonicalUrl(),
                item.contentType(),
                item.publishedAt().toString());
    }
}

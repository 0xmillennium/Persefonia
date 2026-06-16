package dev.persefonia.webpublic.series;

import dev.persefonia.contentpublishing.application.query.PublicSeriesEntryItem;
import dev.persefonia.contentpublishing.application.query.PublicSeriesPageResult;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.webpublic.FrontendAssetResolver;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class PublicSeriesPageViewModelFactory {
    private static final String MAIN_FRONTEND_ENTRY = "src/main.ts";
    private final FrontendAssetResolver assetResolver;

    public PublicSeriesPageViewModelFactory(FrontendAssetResolver assetResolver) {
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
    }

    public PublicSeriesPage page(
            PublicSeriesPageResult series,
            DiscoveryLanguage language,
            String publicUrl,
            String canonicalUrl) {
        return new PublicSeriesPage(
                series.title(),
                series.slug(),
                series.description(),
                series.status().name(),
                language.name().toLowerCase(Locale.ROOT),
                publicUrl,
                canonicalUrl,
                true,
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY),
                series.entries().stream().map(PublicSeriesPageViewModelFactory::entry).toList());
    }

    private static PublicSeriesEntryView entry(PublicSeriesEntryItem item) {
        return new PublicSeriesEntryView(
                item.position(),
                item.title(),
                item.summary(),
                item.publicUrl(),
                item.canonicalUrl(),
                item.contentType(),
                item.publishedAt().toString(),
                item.language());
    }
}

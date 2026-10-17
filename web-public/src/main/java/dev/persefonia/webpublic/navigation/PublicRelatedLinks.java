package dev.persefonia.webpublic.navigation;

import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.service.PublicContentNavigationQueryService;
import dev.persefonia.webpublic.series.DiscoveryPublicSeriesRouteOutcome;
import dev.persefonia.webpublic.series.DiscoveryPublicSeriesRouteResolver;
import dev.persefonia.webpublic.series.PublicSeriesRouteParser;
import dev.persefonia.webpublic.tags.DiscoveryPublicTagRouteOutcome;
import dev.persefonia.webpublic.tags.DiscoveryPublicTagRouteResolver;
import dev.persefonia.webpublic.tags.PublicTagRouteParser;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class PublicRelatedLinks {
    private final ObjectProvider<PublicContentNavigationQueryService> navigation;
    private final DiscoveryPublicTagRouteResolver tags;
    private final DiscoveryPublicSeriesRouteResolver series;

    public PublicRelatedLinks(ObjectProvider<PublicContentNavigationQueryService> navigation,
            DiscoveryPublicTagRouteResolver tags, DiscoveryPublicSeriesRouteResolver series) {
        this.navigation = navigation;
        this.tags = tags;
        this.series = series;
    }

    public java.util.Optional<PublicNavigationLink> tag(String language, String name, String slug) {
        return new PublicTagRouteParser().parse(language, slug).map(tags::resolve)
                .filter(DiscoveryPublicTagRouteOutcome.Tag.class::isInstance)
                .map(DiscoveryPublicTagRouteOutcome.Tag.class::cast)
                .map(route -> new PublicNavigationLink(name, route.publicUrl()));
    }

    public List<PublicNavigationLink> content(PublicContentBySourceQuery query, String language) {
        var service = navigation.getIfAvailable();
        if (service == null) return List.of();
        return service.find(query).map(related -> {
            var links = new java.util.ArrayList<PublicNavigationLink>();
            related.tags().forEach(tag -> tag(language, tag.name(), tag.slug()).ifPresent(links::add));
            related.series().forEach(item -> new PublicSeriesRouteParser().parse(language, item.slug())
                    .map(series::resolve).filter(DiscoveryPublicSeriesRouteOutcome.Series.class::isInstance)
                    .map(DiscoveryPublicSeriesRouteOutcome.Series.class::cast)
                    .filter(route -> route.query().seriesId().equals(item.id())
                            && route.publicUrl().equals(item.publicPath()))
                    .ifPresent(route -> links.add(new PublicNavigationLink(item.title(), route.publicUrl()))));
            return List.copyOf(links);
        }).orElse(List.of());
    }
}

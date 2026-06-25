package dev.persefonia.app.webpublic.sitemap;

import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.webpublic.sitemap.PublicCvAvailability;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * App-mediated adapter answering whether a public Active CV page exists, for static sitemap
 * inclusion. Delegates to the existing public Active CV query; the underlying query service is
 * conditional, so an absent service is treated as "no public CV". No Media storage or repository is
 * touched from web-public.
 */
@Component
class ActiveCvPublicAvailabilityAdapter implements PublicCvAvailability {
    private final ObjectProvider<ActiveCvPublicQueryService> queries;

    ActiveCvPublicAvailabilityAdapter(ObjectProvider<ActiveCvPublicQueryService> queries) {
        this.queries = queries;
    }

    @Override
    public boolean hasPublicCv() {
        ActiveCvPublicQueryService queryService = queries.getIfAvailable();
        return queryService != null && queryService.defaultLanguageView().isPresent();
    }
}

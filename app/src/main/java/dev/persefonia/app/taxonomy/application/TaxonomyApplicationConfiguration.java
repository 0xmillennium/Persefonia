package dev.persefonia.app.taxonomy.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.taxonomy.application.discovery.TagDiscoverabilityCoordinator;
import dev.persefonia.taxonomy.application.discovery.TagDiscoveryProjectionFactory;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.service.TagAdminQueryService;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import dev.persefonia.taxonomy.application.service.PublicTagBySourceQueryHandler;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
class TaxonomyApplicationConfiguration {
    @Bean
    TaxonomyCommandAuthorizationPolicy taxonomyCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessTaxonomyCommandAuthorizationPolicy(policy);
    }

    @Bean
    TagNormalizationService tagNormalizationService() {
        return new TagNormalizationService();
    }

    @Bean
    TagCommandService tagCommandService(
            TagRepository tags,
            TagNormalizationService normalization,
            TaxonomyCommandAuthorizationPolicy authorization,
            TagDiscoverabilityCoordinator discoverability) {
        return new TagCommandService(tags, normalization, authorization, discoverability);
    }

    @Bean
    TagDiscoveryProjectionFactory tagDiscoveryProjectionFactory(
            @Value("${site.public-base-url}") String publicBaseUrl,
            TagPublicRouteFactory routeFactory) {
        return new TagDiscoveryProjectionFactory(publicBaseUrl, routeFactory);
    }

    @Bean
    TagDiscoverabilityCoordinator tagDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            TagDiscoveryProjectionFactory projectionFactory) {
        return new TagDiscoverabilityCoordinator(updatePort, projectionFactory);
    }

    @Bean
    TagAdminQueryService tagAdminQueryService(TagRepository tags, TaxonomyCommandAuthorizationPolicy authorization) {
        return new TagAdminQueryService(tags, authorization);
    }

    @Bean
    TagVocabularyQueryService tagVocabularyQueryService(TagRepository tags) {
        return new TagVocabularyQueryService(tags);
    }

    @Bean
    PublicTagBySourceQueryHandler publicTagBySourceQueryHandler(TagRepository tags) {
        return new PublicTagBySourceQueryHandler(tags);
    }
}

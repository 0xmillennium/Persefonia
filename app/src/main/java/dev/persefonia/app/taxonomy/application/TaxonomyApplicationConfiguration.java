package dev.persefonia.app.taxonomy.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.service.TagAdminQueryService;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            TaxonomyCommandAuthorizationPolicy authorization) {
        return new TagCommandService(tags, normalization, authorization);
    }

    @Bean
    TagAdminQueryService tagAdminQueryService(TagRepository tags, TaxonomyCommandAuthorizationPolicy authorization) {
        return new TagAdminQueryService(tags, authorization);
    }
}

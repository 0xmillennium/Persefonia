package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsAdminQueryService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProfilePortfolioApplicationConfiguration {
    @Bean
    PortfolioCommandAuthorizationPolicy portfolioCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessPortfolioCommandAuthorizationPolicy(policy);
    }

    @Bean
    SitePresentationSettingsCommandService sitePresentationSettingsCommandService(
            SitePresentationSettingsRepository settings,
            PortfolioCommandAuthorizationPolicy authorization) {
        return new SitePresentationSettingsCommandService(settings, authorization);
    }

    @Bean
    SitePresentationSettingsAdminQueryService sitePresentationSettingsAdminQueryService(
            SitePresentationSettingsRepository settings) {
        return new SitePresentationSettingsAdminQueryService(settings);
    }

    @Bean
    PublicHomepageSettingsQueryService publicHomepageSettingsQueryService(
            SitePresentationSettingsRepository settings) {
        return new PublicHomepageSettingsQueryService(settings);
    }
}

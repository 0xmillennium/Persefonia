package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.profileportfolio.application.discovery.ConfiguredProjectCanonicalUrlFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectDiscoverabilityCoordinator;
import dev.persefonia.profileportfolio.application.discovery.ProjectDiscoveryProjectionFactory;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicExposurePolicy;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicMutationFactsFactory;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.service.ActiveCvAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandService;
import dev.persefonia.profileportfolio.application.service.ProjectAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ProjectCommandService;
import dev.persefonia.profileportfolio.application.service.PublicFeaturedProjectQueryService;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProjectDetailQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProjectListingQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProfileSummaryQueryService;
import dev.persefonia.profileportfolio.application.service.PersonalProfileAdminQueryService;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsAdminQueryService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

    @Bean
    PersonalProfileCommandService personalProfileCommandService(
            PersonalProfileRepository profiles,
            SitePresentationSettingsRepository settings,
            PortfolioCommandAuthorizationPolicy authorization) {
        return new PersonalProfileCommandService(profiles, settings, authorization);
    }

    @Bean
    PersonalProfileAdminQueryService personalProfileAdminQueryService(
            PersonalProfileRepository profiles,
            SitePresentationSettingsRepository settings) {
        return new PersonalProfileAdminQueryService(profiles, settings);
    }

    @Bean
    @ConditionalOnBean(ActiveCvAssetEligibilityPort.class)
    ActiveCvCommandService activeCvCommandService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvAssetEligibilityPort eligibility,
            PortfolioCommandAuthorizationPolicy authorization) {
        return new ActiveCvCommandService(activeCvProfiles, settings, eligibility, authorization);
    }

    @Bean
    @ConditionalOnBean(ActiveCvAssetEligibilityPort.class)
    ActiveCvAdminQueryService activeCvAdminQueryService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvAssetEligibilityPort eligibility) {
        return new ActiveCvAdminQueryService(activeCvProfiles, settings, eligibility);
    }

    @Bean
    ProjectTagVocabularyPort projectTagVocabularyPort(TagVocabularyQueryService vocabulary) {
        return new TaxonomyProjectTagVocabularyAdapter(vocabulary);
    }

    @Bean
    ProjectCommandService projectCommandService(
            ProjectRepository projects,
            SitePresentationSettingsRepository settings,
            ProjectTagVocabularyPort tags,
            PortfolioCommandAuthorizationPolicy authorization,
            ProjectDiscoverabilityCoordinator discoverability,
            ProjectPublicMutationFactsFactory publicFacts) {
        return new ProjectCommandService(projects, settings, tags, authorization, discoverability, publicFacts);
    }

    @Bean
    ProjectAdminQueryService projectAdminQueryService(
            ProjectAdminReadModel projects,
            SitePresentationSettingsRepository settings,
            ProjectTagVocabularyPort tags) {
        return new ProjectAdminQueryService(projects, settings, tags);
    }

    @Bean
    PublicProfileSummaryQueryService publicProfileSummaryQueryService(PersonalProfileRepository profiles) {
        return new PublicProfileSummaryQueryService(profiles);
    }

    @Bean
    ProjectPublicRouteFactory projectPublicRouteFactory() {
        return new ProjectPublicRouteFactory();
    }

    @Bean
    ProjectPublicExposurePolicy projectPublicExposurePolicy() {
        return new ProjectPublicExposurePolicy();
    }

    @Bean
    ProjectPublicMutationFactsFactory projectPublicMutationFactsFactory(
            ProjectPublicExposurePolicy policy, ProjectPublicRouteFactory routes) {
        return new ProjectPublicMutationFactsFactory(policy, routes);
    }

    @Bean
    ConfiguredProjectCanonicalUrlFactory configuredProjectCanonicalUrlFactory(
            @Value("${site.public-base-url}") String publicBaseUrl) {
        return new ConfiguredProjectCanonicalUrlFactory(publicBaseUrl);
    }

    @Bean
    ProjectDiscoveryProjectionFactory projectDiscoveryProjectionFactory(
            ProjectPublicRouteFactory routeFactory,
            ConfiguredProjectCanonicalUrlFactory canonicalUrlFactory,
            ProjectPublicExposurePolicy exposurePolicy) {
        return new ProjectDiscoveryProjectionFactory(routeFactory, canonicalUrlFactory, exposurePolicy);
    }

    @Bean
    ProjectDiscoverabilityCoordinator projectDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            ProjectDiscoveryProjectionFactory projectionFactory) {
        return new ProjectDiscoverabilityCoordinator(updatePort, removePort, projectionFactory);
    }

    @Bean
    PublicProjectListingQueryService publicProjectListingQueryService(
            ProjectPublicReadModel projects,
            ProjectTagVocabularyPort tags,
            ProjectPublicRouteFactory routeFactory) {
        return new PublicProjectListingQueryService(projects, tags, routeFactory);
    }

    @Bean
    PublicProjectDetailQueryService publicProjectDetailQueryService(
            ProjectPublicReadModel projects,
            ProjectTagVocabularyPort tags,
            ProjectPublicRouteFactory routeFactory) {
        return new PublicProjectDetailQueryService(projects, tags, routeFactory);
    }

    @Bean
    PublicFeaturedProjectQueryService publicFeaturedProjectQueryService(
            ProjectPublicReadModel projects,
            ProjectTagVocabularyPort tags,
            ProjectPublicRouteFactory routeFactory) {
        return new PublicFeaturedProjectQueryService(projects, tags, routeFactory);
    }
}

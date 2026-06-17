package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.service.ProjectAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ProjectCommandService;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProfileSummaryQueryService;
import dev.persefonia.profileportfolio.application.service.PersonalProfileAdminQueryService;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsAdminQueryService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
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
    ProjectTagVocabularyPort projectTagVocabularyPort(TagVocabularyQueryService vocabulary) {
        return new TaxonomyProjectTagVocabularyAdapter(vocabulary);
    }

    @Bean
    ProjectCommandService projectCommandService(
            ProjectRepository projects,
            SitePresentationSettingsRepository settings,
            ProjectTagVocabularyPort tags,
            PortfolioCommandAuthorizationPolicy authorization) {
        return new ProjectCommandService(projects, settings, tags, authorization);
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
}

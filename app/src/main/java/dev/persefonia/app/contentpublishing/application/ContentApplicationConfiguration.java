package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.contentpublishing.rendering.CommonmarkMarkdownRenderingService;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryRedirectFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.discovery.SeriesDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.SeriesDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.PublicSeriesEntryReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTaggedContentReadModel;
import dev.persefonia.contentpublishing.application.port.PublicTranslationReadModel;
import dev.persefonia.contentpublishing.application.port.SeriesCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.port.TranslationCandidateContentReadModel;
import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import dev.persefonia.contentpublishing.application.service.ContentDraftCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentLifecycleCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentPreviewQueryHandler;
import dev.persefonia.contentpublishing.application.service.ContentPublishCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.contentpublishing.application.service.PublicContentQueryHandler;
import dev.persefonia.contentpublishing.application.service.PublicTaggedContentQueryHandler;
import dev.persefonia.contentpublishing.application.service.PublicSeriesPageQueryService;
import dev.persefonia.contentpublishing.application.service.PublicTranslationLinkQueryService;
import dev.persefonia.contentpublishing.application.service.SeriesAdminQueryService;
import dev.persefonia.contentpublishing.application.service.SeriesCommandService;
import dev.persefonia.contentpublishing.application.service.TranslationGroupAdminQueryService;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContentApplicationConfiguration {
    @Bean
    MarkdownRenderingService markdownRenderingService() {
        return new CommonmarkMarkdownRenderingService();
    }

    @Bean
    ContentCommandAuthorizationPolicy contentCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessContentCommandAuthorizationPolicy(policy);
    }

    @Bean
    ContentPublishingEventPublisher contentPublishingEventPublisher() {
        return new NoOpContentPublishingEventPublisher();
    }

    @Bean
    ContentPublicRouteFactory contentPublicRouteFactory() {
        return new ContentPublicRouteFactory();
    }

    @Bean
    ConfiguredContentCanonicalUrlFactory configuredContentCanonicalUrlFactory(
            @Value("${site.public-base-url}") String publicBaseUrl) {
        return new ConfiguredContentCanonicalUrlFactory(publicBaseUrl);
    }

    @Bean
    ContentDiscoveryProjectionFactory contentDiscoveryProjectionFactory(
            ContentPublicRouteFactory routeFactory,
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        return new ContentDiscoveryProjectionFactory(routeFactory, canonicalUrlFactory);
    }

    @Bean
    ContentDiscoveryRedirectFactory contentDiscoveryRedirectFactory(ContentPublicRouteFactory routeFactory) {
        return new ContentDiscoveryRedirectFactory(routeFactory);
    }

    @Bean
    ContentDiscoverabilityCoordinator contentDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            CreateRedirectRulePort redirectPort,
            ContentDiscoveryProjectionFactory projectionFactory,
            ContentDiscoveryRedirectFactory redirectFactory) {
        return new ContentDiscoverabilityCoordinator(updatePort, removePort, redirectPort, projectionFactory, redirectFactory);
    }

    @Bean
    SeriesDiscoveryProjectionFactory seriesDiscoveryProjectionFactory(
            ConfiguredContentCanonicalUrlFactory canonicalUrlFactory) {
        return new SeriesDiscoveryProjectionFactory(canonicalUrlFactory);
    }

    @Bean
    SeriesDiscoverabilityCoordinator seriesDiscoverabilityCoordinator(
            UpdateDiscoverableResourcePort updatePort,
            RemoveDiscoverableResourcePort removePort,
            SeriesDiscoveryProjectionFactory projectionFactory) {
        return new SeriesDiscoverabilityCoordinator(updatePort, removePort, projectionFactory);
    }

    @Bean
    ContentCommandService contentCommandService(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            MarkdownRenderingService renderer,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events,
            ContentDiscoverabilityCoordinator discoverability) {
        return new ContentCommandService(
                new ContentDraftCommandHandler(contentItems, authorization, events, discoverability),
                new ContentPreviewQueryHandler(contentItems, renderer, authorization),
                new ContentPublishCommandHandler(contentItems, revisions, renderer, authorization, events, discoverability),
                new ContentLifecycleCommandHandler(contentItems, authorization, events, discoverability));
    }

    @Bean
    ContentRevisionQueryHandler contentRevisionQueryHandler(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            ContentCommandAuthorizationPolicy authorization) {
        return new ContentRevisionQueryHandler(contentItems, revisions, authorization);
    }

    @Bean
    ContentAdminQueryService contentAdminQueryService(
            ContentItemRepository contentItems,
            ContentCommandAuthorizationPolicy authorization) {
        return new ContentAdminQueryService(contentItems, authorization);
    }

    @Bean
    ContentTagVocabularyPort contentTagVocabularyPort(TagVocabularyQueryService vocabulary) {
        return new TaxonomyContentTagVocabularyAdapter(vocabulary);
    }

    @Bean
    ContentTagAssignmentService contentTagAssignmentService(
            ContentItemRepository contentItems,
            ContentTagAssignmentStore assignments,
            ContentTagVocabularyPort vocabulary,
            ContentCommandAuthorizationPolicy authorization) {
        return new ContentTagAssignmentService(contentItems, assignments, vocabulary, authorization);
    }

    @Bean
    TranslationGroupCommandService translationGroupCommandService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            ContentCommandAuthorizationPolicy authorization) {
        return new TranslationGroupCommandService(contentItems, translationGroups, authorization);
    }

    @Bean
    TranslationGroupAdminQueryService translationGroupAdminQueryService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            TranslationCandidateContentReadModel translationCandidates,
            ContentCommandAuthorizationPolicy authorization) {
        return new TranslationGroupAdminQueryService(contentItems, translationGroups, translationCandidates, authorization);
    }

    @Bean
    SeriesCommandService seriesCommandService(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            ContentCommandAuthorizationPolicy authorization,
            SeriesDiscoverabilityCoordinator discoverability) {
        return new SeriesCommandService(contentItems, seriesRepository, authorization, discoverability);
    }

    @Bean
    SeriesAdminQueryService seriesAdminQueryService(
            ContentItemRepository contentItems,
            SeriesRepository seriesRepository,
            SeriesCandidateContentReadModel seriesCandidates,
            ContentCommandAuthorizationPolicy authorization) {
        return new SeriesAdminQueryService(contentItems, seriesRepository, seriesCandidates, authorization);
    }

    @Bean
    PublicContentQueryHandler publicContentQueryHandler(
            ContentItemRepository contentItems,
            ContentPublicRouteFactory routeFactory) {
        return new PublicContentQueryHandler(contentItems, routeFactory);
    }

    @Bean
    PublicContentBySourceQueryHandler publicContentBySourceQueryHandler(
            ContentItemRepository contentItems,
            ContentPublicRouteFactory routeFactory) {
        return new PublicContentBySourceQueryHandler(contentItems, routeFactory);
    }

    @Bean
    PublicTranslationLinkQueryService publicTranslationLinkQueryService(PublicTranslationReadModel readModel) {
        return new PublicTranslationLinkQueryService(readModel);
    }

    @Bean
    PublicTaggedContentQueryHandler publicTaggedContentQueryHandler(PublicTaggedContentReadModel readModel) {
        return new PublicTaggedContentQueryHandler(readModel);
    }

    @Bean
    PublicSeriesPageQueryService publicSeriesPageQueryService(
            SeriesRepository seriesRepository,
            PublicSeriesEntryReadModel entries) {
        return new PublicSeriesPageQueryService(seriesRepository, entries);
    }
}

package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.contentpublishing.rendering.CommonmarkMarkdownRenderingService;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingService;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import dev.persefonia.contentpublishing.application.service.ContentDraftCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentLifecycleCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentPreviewQueryHandler;
import dev.persefonia.contentpublishing.application.service.ContentPublishCommandHandler;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
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
    ContentCommandService contentCommandService(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            MarkdownRenderingService renderer,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events,
            ContentRevisionQueryHandler revisionQueries) {
        return new ContentCommandService(
                new ContentDraftCommandHandler(contentItems, authorization, events),
                new ContentPreviewQueryHandler(contentItems, renderer, authorization),
                new ContentPublishCommandHandler(contentItems, revisions, renderer, authorization, events),
                new ContentLifecycleCommandHandler(contentItems, authorization, events),
                revisionQueries);
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
}

package dev.persefonia.app.platformoperations.cache.integration;

import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.contentpublishing.application.port.ContentPublicSurfaceDependencyQuery;
import dev.persefonia.contentpublishing.application.port.PublicTranslationMemberRouteQuery;
import dev.persefonia.medialibrary.application.publicview.AssetPublicVariantRouteQuery;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicSurfaceDependencyQuery;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import dev.persefonia.taxonomy.application.port.TagPublicRouteQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration(proxyBeanMethods = false)
class PublicCacheIntegrationConfiguration {
    @Bean
    TagPublicRouteFactory tagPublicRouteFactory() { return new TagPublicRouteFactory(); }

    @Bean
    PublicCacheTargetPlanner publicCacheTargetPlanner() { return new PublicCacheTargetPlanner(); }

    @Bean
    PublicCacheInvalidationCoordinator publicCacheInvalidationCoordinator(
            ContentPublicSurfaceDependencyQuery contentDependencies,
            PublicTranslationMemberRouteQuery translationMembers,
            TagPublicRouteQuery tagRouteQuery,
            ProjectPublicSurfaceDependencyQuery projectDependencies,
            AssetPublicVariantRouteQuery assetVariantRoutes,
            CacheInvalidationExecutionPort execution,
            PublicCacheTargetPlanner planner,
            TagPublicRouteFactory tagRoutes,
            ProjectPublicRouteFactory projectRoutes) {
        return new PublicCacheInvalidationCoordinator(
                contentDependencies, translationMembers, tagRouteQuery, projectDependencies,
                assetVariantRoutes, execution, planner, tagRoutes, projectRoutes);
    }

    @Bean
    PublicCacheInvalidationRegistrar publicCacheInvalidationRegistrar(
            PostCommitTaskExecutor postCommitTasks,
            ObjectProvider<PublicCacheInvalidationCoordinator> coordinator) {
        PublicCacheInvalidationCoordinator available = coordinator.getIfAvailable();
        return available == null
                ? PublicCacheInvalidationRegistrar.noOp()
                : new PublicCacheInvalidationRegistrar(postCommitTasks, available);
    }
}

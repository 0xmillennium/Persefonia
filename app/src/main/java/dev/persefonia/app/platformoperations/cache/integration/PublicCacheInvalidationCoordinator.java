package dev.persefonia.app.platformoperations.cache.integration;

import static dev.persefonia.app.platformoperations.cache.integration.PublicCacheTargetPlanner.DEPENDENCY_QUERY_LIMIT;

import dev.persefonia.contentpublishing.application.port.ContentPublicSurfaceDependencyQuery;
import dev.persefonia.contentpublishing.application.port.PublicTranslationMemberRouteQuery;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.medialibrary.application.publicview.AssetPublicVariantRouteQuery;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicSurfaceDependencyQuery;
import dev.persefonia.profileportfolio.application.publicview.PublicCvRoutes;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import dev.persefonia.taxonomy.application.port.TagPublicRouteQuery;
import dev.persefonia.taxonomy.domain.model.TagId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PublicCacheInvalidationCoordinator {
    private static final String HOME = "/";
    private static final String FEED = "/feed.xml";
    private static final String SITEMAP = "/sitemap.xml";

    private final ContentPublicSurfaceDependencyQuery contentDependencies;
    private final PublicTranslationMemberRouteQuery translationMembers;
    private final TagPublicRouteQuery tagRouteQuery;
    private final ProjectPublicSurfaceDependencyQuery projectDependencies;
    private final AssetPublicVariantRouteQuery assetVariantRoutes;
    private final CacheInvalidationExecutionPort execution;
    private final PublicCacheTargetPlanner planner;
    private final TagPublicRouteFactory tagRoutes;
    private final ProjectPublicRouteFactory projectRoutes;

    public PublicCacheInvalidationCoordinator(
            ContentPublicSurfaceDependencyQuery contentDependencies,
            PublicTranslationMemberRouteQuery translationMembers,
            TagPublicRouteQuery tagRouteQuery,
            ProjectPublicSurfaceDependencyQuery projectDependencies,
            AssetPublicVariantRouteQuery assetVariantRoutes,
            CacheInvalidationExecutionPort execution,
            PublicCacheTargetPlanner planner,
            TagPublicRouteFactory tagRoutes,
            ProjectPublicRouteFactory projectRoutes) {
        this.contentDependencies = contentDependencies;
        this.translationMembers = translationMembers;
        this.tagRouteQuery = tagRouteQuery;
        this.projectDependencies = projectDependencies;
        this.assetVariantRoutes = assetVariantRoutes;
        this.execution = execution;
        this.planner = planner;
        this.tagRoutes = tagRoutes;
        this.projectRoutes = projectRoutes;
    }

    public void handle(PublicCacheInvalidationSignal signal) {
        Collection<String> routes = switch (signal) {
            case PublicCacheInvalidationSignal.ContentChanged changed -> contentRoutes(changed);
            case PublicCacheInvalidationSignal.ContentTagsChanged changed -> contentTagRoutes(changed);
            case PublicCacheInvalidationSignal.SeriesChanged changed -> seriesRoutes(changed);
            case PublicCacheInvalidationSignal.TranslationGroupChanged changed -> translationRoutes(changed);
            case PublicCacheInvalidationSignal.TagChanged changed -> tagRoutes(changed);
            case PublicCacheInvalidationSignal.ProjectChanged changed -> projectRoutes(changed);
            case PublicCacheInvalidationSignal.PersonalProfileChanged ignored -> List.of(HOME);
            case PublicCacheInvalidationSignal.SiteSettingsChanged ignored -> withHome(PublicCvRoutes.ALL);
            case PublicCacheInvalidationSignal.ActiveCvChanged ignored -> PublicCvRoutes.ALL;
            case PublicCacheInvalidationSignal.AssetVisibilityChanged changed -> assetRoutes(changed);
            case PublicCacheInvalidationSignal.RedirectChanged changed -> List.of(changed.sourceUrl().value());
        };
        planner.plan(routes).ifPresent(execution::requestAndExecute);
    }

    private Collection<String> contentRoutes(PublicCacheInvalidationSignal.ContentChanged changed) {
        var facts = changed.facts();
        List<String> routes = new ArrayList<>();
        if (facts.beforeExposure().directReachable()) facts.oldPublicRoute().ifPresent(route -> add(routes, route));
        if (facts.afterExposure().directReachable()) facts.currentPublicRoute().ifPresent(route -> add(routes, route));
        if (facts.beforeExposure().listed() || facts.afterExposure().listed()) {
            var dependencies = contentDependencies.findFor(facts.contentId(), DEPENDENCY_QUERY_LIMIT);
            if (dependencies.overflow()) throw new PublicCacheTargetPlanner.PublicCacheTargetOverflowException();
            addAll(routes, dependencies.tagRoutes());
            addAll(routes, dependencies.activeSeriesRoutes());
            addAll(routes, dependencies.publicTranslationMemberRoutes());
        }
        if (facts.beforeExposure().sitemapEligible() || facts.afterExposure().sitemapEligible()) routes.add(SITEMAP);
        if (facts.beforeExposure().feedEligible() || facts.afterExposure().feedEligible()) routes.add(FEED);
        return routes;
    }

    private Collection<String> contentTagRoutes(PublicCacheInvalidationSignal.ContentTagsChanged changed) {
        var facts = changed.facts();
        if (!facts.membershipChanged() || !facts.publicListed()) return List.of();
        Set<TagId> tagIds = new LinkedHashSet<>();
        facts.oldTagIds().forEach(id -> tagIds.add(TagId.from(id.value())));
        facts.newTagIds().forEach(id -> tagIds.add(TagId.from(id.value())));
        var routes = tagRouteQuery.findActiveRoutes(
                tagIds, DiscoveryLanguage.valueOf(facts.language().name()), DEPENDENCY_QUERY_LIMIT);
        rejectDependencyOverflow(routes);
        return values(routes);
    }

    private Collection<String> seriesRoutes(PublicCacheInvalidationSignal.SeriesChanged changed) {
        if (!changed.result().mutated()) return List.of();
        List<String> routes = new ArrayList<>();
        changed.result().oldPublicRoute().ifPresent(route -> add(routes, route));
        changed.result().currentPublicRoute().ifPresent(route -> add(routes, route));
        return routes;
    }

    private Collection<String> translationRoutes(PublicCacheInvalidationSignal.TranslationGroupChanged changed) {
        if (!changed.result().publicMembershipChanged()) return List.of();
        var current = translationMembers.findPublicMemberRoutes(
                changed.result().translationGroupId(), DEPENDENCY_QUERY_LIMIT);
        rejectDependencyOverflow(current);
        List<String> routes = new ArrayList<>(values(current));
        changed.result().removedPublicRoute().ifPresent(route -> add(routes, route));
        return routes;
    }

    private Collection<String> tagRoutes(PublicCacheInvalidationSignal.TagChanged changed) {
        if (!changed.result().mutated()) return List.of();
        List<String> routes = new ArrayList<>();
        changed.result().oldSlug().ifPresent(slug -> addAll(routes, tagRoutes.allLanguageRoutes(slug)));
        if (changed.result().currentSlug() != null) addAll(routes, tagRoutes.allLanguageRoutes(changed.result().currentSlug()));

        var projects = projectDependencies.findReferencing(
                dev.persefonia.profileportfolio.domain.common.TagId.from(changed.result().tagId().value()),
                DEPENDENCY_QUERY_LIMIT);
        rejectDependencyOverflow(projects);
        projects.forEach(project -> {
            addAll(routes, project.directRoutes().values());
            if (project.listed()) project.directRoutes().keySet().forEach(
                    language -> add(routes, projectRoutes.listingUrl(language)));
            if (project.featured()) routes.add(HOME);
        });
        return routes;
    }

    private Collection<String> projectRoutes(PublicCacheInvalidationSignal.ProjectChanged changed) {
        var facts = changed.facts();
        List<String> routes = new ArrayList<>();
        if (facts.beforeExposure().directReachable()) addAll(routes, facts.beforeRoutes().values());
        if (facts.afterExposure().directReachable()) addAll(routes, facts.afterRoutes().values());
        if (facts.beforeExposure().listed()) facts.beforeRoutes().keySet().forEach(
                language -> add(routes, projectRoutes.listingUrl(language)));
        if (facts.afterExposure().listed()) facts.afterRoutes().keySet().forEach(
                language -> add(routes, projectRoutes.listingUrl(language)));
        if (facts.beforeExposure().homepageFeaturedEligible() || facts.afterExposure().homepageFeaturedEligible()) {
            routes.add(HOME);
        }
        if (facts.beforeExposure().sitemapEligible() || facts.afterExposure().sitemapEligible()) routes.add(SITEMAP);
        return routes;
    }

    private Collection<String> assetRoutes(PublicCacheInvalidationSignal.AssetVisibilityChanged changed) {
        if (changed.beforeVisibility() != AssetVisibility.PUBLIC
                && changed.afterVisibility() != AssetVisibility.PUBLIC) return List.of();
        var routes = assetVariantRoutes.findStableVariantRoutes(changed.assetId(), DEPENDENCY_QUERY_LIMIT);
        rejectDependencyOverflow(routes);
        return routes;
    }

    private static List<String> withHome(List<String> routes) {
        List<String> result = new ArrayList<>();
        result.add(HOME);
        result.addAll(routes);
        return result;
    }

    private static void rejectDependencyOverflow(Collection<?> values) {
        if (values.size() >= DEPENDENCY_QUERY_LIMIT) {
            throw new PublicCacheTargetPlanner.PublicCacheTargetOverflowException();
        }
    }

    private static List<String> values(Collection<PublicUrl> routes) {
        return routes.stream().map(PublicUrl::value).toList();
    }

    private static void add(List<String> routes, PublicUrl route) { routes.add(route.value()); }
    private static void addAll(List<String> routes, Collection<PublicUrl> additions) {
        additions.forEach(route -> add(routes, route));
    }
}

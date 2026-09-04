package dev.persefonia.app.platformoperations.cache.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.publicview.*;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.platformoperations.application.cache.*;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.publicview.*;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.discovery.TagPublicRouteFactory;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PublicCacheInvalidationSignalMatrixTest {
    private final CapturingExecution execution = new CapturingExecution();

    @Test
    void contentCoversPublicUnlistedPrivateUnpublishArchivePageAndArchivedTagDependency() {
        ContentId id = ContentId.newId();
        var listed = new ContentPublicExposureSnapshot(true, true, true, true);
        var unlisted = new ContentPublicExposureSnapshot(true, false, false, false);
        coordinator((ignored, limit) -> new ContentPublicSurfaceDependencies(
                List.of(new PublicUrl("/en/tags/archived")), List.of(), List.of(), false))
                .handle(new PublicCacheInvalidationSignal.ContentChanged(new ContentPublicMutationFacts(
                        id, listed, listed, Optional.of(new PublicUrl("/en/articles/a")),
                        Optional.of(new PublicUrl("/en/articles/a")))));
        assertTargets("/en/articles/a", "/en/tags/archived", "/feed.xml", "/sitemap.xml");

        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.ContentChanged(new ContentPublicMutationFacts(
                id, unlisted, unlisted, Optional.of(new PublicUrl("/en/pages/a")),
                Optional.of(new PublicUrl("/en/pages/a")))));
        assertTargets("/en/pages/a");

        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.ContentChanged(new ContentPublicMutationFacts(
                id, ContentPublicExposureSnapshot.none(), ContentPublicExposureSnapshot.none(),
                Optional.empty(), Optional.empty())));
        assertThat(execution.requests).isEmpty();

        execution.clear();
        coordinator((ignored, limit) -> new ContentPublicSurfaceDependencies(
                List.of(new PublicUrl("/en/tags/archived")), List.of(), List.of(), false))
                .handle(new PublicCacheInvalidationSignal.ContentChanged(new ContentPublicMutationFacts(
                        id, listed, ContentPublicExposureSnapshot.none(), Optional.of(new PublicUrl("/en/articles/a")),
                        Optional.empty())));
        assertTargets("/en/articles/a", "/en/tags/archived", "/feed.xml", "/sitemap.xml");
    }

    @Test
    void contentTagsUseUnionIncludingRemovedArchivedTagsAndNoOpForUnchangedOrNonlistedContent() {
        var oldArchived = dev.persefonia.contentpublishing.domain.content.TagId.newId();
        var active = dev.persefonia.contentpublishing.domain.content.TagId.newId();
        var tagQuery = new dev.persefonia.taxonomy.application.port.TagPublicRouteQuery() {
            @Override public List<PublicUrl> findExistingPublicRoutes(
                    Set<dev.persefonia.taxonomy.domain.model.TagId> ids,
                    dev.persefonia.discovery.application.contract.DiscoveryLanguage language, int limit) {
                assertThat(ids).extracting(id -> id.value()).containsExactlyInAnyOrder(oldArchived.value(), active.value());
                return List.of(new PublicUrl("/en/tags/archived"), new PublicUrl("/en/tags/active"));
            }
        };
        var facts = new ContentTagMutationFacts(ContentId.newId(), ContentLanguage.EN, true,
                Set.of(oldArchived, active), Set.of(active), true);
        coordinator(null, null, tagQuery, null, null).handle(new PublicCacheInvalidationSignal.ContentTagsChanged(facts));
        assertTargets("/en/tags/active", "/en/tags/archived");

        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.ContentTagsChanged(
                new ContentTagMutationFacts(ContentId.newId(), ContentLanguage.EN, true,
                        Set.of(active), Set.of(active), false)));
        coordinator().handle(new PublicCacheInvalidationSignal.ContentTagsChanged(
                new ContentTagMutationFacts(ContentId.newId(), ContentLanguage.EN, false,
                        Set.of(active), Set.of(), true)));
        assertThat(execution.requests).isEmpty();
    }

    @Test
    void seriesUpdateSlugArchiveAndEntryMutationsTargetOldAndCurrentRoutes() {
        for (var change : PublicCacheInvalidationSignal.SeriesChange.values()) {
            execution.clear();
            coordinator().handle(new PublicCacheInvalidationSignal.SeriesChanged(change,
                    new SeriesResult(SeriesId.newId(), ContentId.newId(), true,
                            Optional.of(new PublicUrl("/en/series/old")),
                            Optional.of(new PublicUrl("/en/series/current")))));
            assertTargets("/en/series/current", "/en/series/old");
        }
        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.SeriesChanged(
                PublicCacheInvalidationSignal.SeriesChange.ARCHIVE,
                new SeriesResult(SeriesId.newId(), null, false, Optional.empty(), Optional.empty())));
        assertThat(execution.requests).isEmpty();
    }

    @Test
    void translationPublicMembershipAddsAndRemovalsInvalidateCurrentAndRemovedRoutes() {
        TranslationGroupId id = TranslationGroupId.newId();
        coordinator(null, (ignored, limit) -> List.of(new PublicUrl("/en/articles/member")), null, null, null)
                .handle(new PublicCacheInvalidationSignal.TranslationGroupChanged(
                        PublicCacheInvalidationSignal.TranslationChange.REMOVE,
                        new TranslationGroupResult(id, ContentId.newId(), true,
                                Optional.of(new PublicUrl("/tr/articles/removed")))));
        assertTargets("/en/articles/member", "/tr/articles/removed");

        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.TranslationGroupChanged(
                PublicCacheInvalidationSignal.TranslationChange.ADD,
                new TranslationGroupResult(id, ContentId.newId(), false, Optional.empty())));
        assertThat(execution.requests).isEmpty();
    }

    @Test
    void tagUpdateArchiveAndProjectDependenciesProduceExactPublicSurfaces() {
        var surface = new ProjectPublicSurface(
                Map.of(dev.persefonia.profileportfolio.domain.common.ContentLanguage.EN,
                        new PublicUrl("/en/projects/example")), true, true);
        for (var change : PublicCacheInvalidationSignal.TagChange.values()) {
            execution.clear();
            coordinator(null, null, null, (id, limit) -> List.of(surface), null)
                    .handle(new PublicCacheInvalidationSignal.TagChanged(change,
                            new TagCommandResult(dev.persefonia.taxonomy.domain.model.TagId.newId(), TagStatus.ACTIVE,
                                    Instant.parse("2026-09-04T10:00:00Z"), true,
                                    Optional.of(TagSlug.ofCanonical("old")), TagSlug.ofCanonical("current"))));
            assertTargets("/", "/en/projects", "/en/projects/example", "/en/tags/current", "/en/tags/old",
                    "/tr/tags/current", "/tr/tags/old");
        }
    }

    @Test
    void projectExposureTargetsDirectListingHomepageAndSitemapWithoutPrivateSurface() {
        var publicExposure = new ProjectPublicExposureSnapshot(true, true, true, true);
        var routes = Map.of(dev.persefonia.profileportfolio.domain.common.ContentLanguage.EN,
                new PublicUrl("/en/projects/example"));
        coordinator().handle(new PublicCacheInvalidationSignal.ProjectChanged(new ProjectPublicMutationFacts(
                UUID.randomUUID(), new ProjectPublicExposureSnapshot(false, false, false, false), publicExposure,
                Map.of(), routes)));
        assertTargets("/", "/en/projects", "/en/projects/example", "/sitemap.xml");

        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.ProjectChanged(new ProjectPublicMutationFacts(
                UUID.randomUUID(), new ProjectPublicExposureSnapshot(false, false, false, false),
                new ProjectPublicExposureSnapshot(false, false, false, false), Map.of(), Map.of())));
        assertThat(execution.requests).isEmpty();
    }

    @Test
    void profileSettingsCvMediaAndRedirectFamiliesHaveExactTargetsAndMediaNoOp() {
        coordinator().handle(new PublicCacheInvalidationSignal.PersonalProfileChanged(UUID.randomUUID()));
        assertTargets("/");
        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.SiteSettingsChanged(UUID.randomUUID()));
        assertThat(targets()).containsExactlyInAnyOrder("/", "/cv", "/cv/download", "/cv/en", "/cv/en/download",
                "/cv/tr", "/cv/tr/download");
        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.ActiveCvChanged(UUID.randomUUID()));
        assertThat(targets()).containsExactlyInAnyOrder("/cv", "/cv/download", "/cv/en", "/cv/en/download",
                "/cv/tr", "/cv/tr/download");
        execution.clear();
        coordinator(null, null, null, null, (id, limit) -> List.of("/media/assets/id/variants/original"))
                .handle(new PublicCacheInvalidationSignal.AssetVisibilityChanged(
                        AssetId.newId(), AssetVisibility.PRIVATE, AssetVisibility.PUBLIC));
        assertTargets("/media/assets/id/variants/original");
        execution.clear();
        coordinator().handle(new PublicCacheInvalidationSignal.AssetVisibilityChanged(
                AssetId.newId(), AssetVisibility.PRIVATE, AssetVisibility.PRIVATE));
        assertThat(execution.requests).isEmpty();
        coordinator().handle(new PublicCacheInvalidationSignal.RedirectChanged(UUID.randomUUID(), new PublicUrl("/old")));
        assertTargets("/old");
    }

    private PublicCacheInvalidationCoordinator coordinator() { return coordinator(null, null, null, null, null); }
    private PublicCacheInvalidationCoordinator coordinator(
            dev.persefonia.contentpublishing.application.port.ContentPublicSurfaceDependencyQuery content) {
        return coordinator(content, null, null, null, null);
    }
    private PublicCacheInvalidationCoordinator coordinator(
            dev.persefonia.contentpublishing.application.port.ContentPublicSurfaceDependencyQuery content,
            dev.persefonia.contentpublishing.application.port.PublicTranslationMemberRouteQuery translations,
            dev.persefonia.taxonomy.application.port.TagPublicRouteQuery tags,
            dev.persefonia.profileportfolio.application.port.ProjectPublicSurfaceDependencyQuery projects,
            dev.persefonia.medialibrary.application.publicview.AssetPublicVariantRouteQuery assets) {
        return new PublicCacheInvalidationCoordinator(
                content == null ? (id, limit) -> new ContentPublicSurfaceDependencies(List.of(), List.of(), List.of(), false) : content,
                translations == null ? (id, limit) -> List.of() : translations,
                tags == null ? (ids, language, limit) -> List.of() : tags,
                projects == null ? (id, limit) -> List.of() : projects,
                assets == null ? (id, limit) -> List.of() : assets,
                execution, new PublicCacheTargetPlanner(), new TagPublicRouteFactory(), new ProjectPublicRouteFactory());
    }

    private List<String> targets() {
        return execution.requests.getFirst().targets().stream().map(CacheInvalidationTargetRequest::value).toList();
    }
    private void assertTargets(String... expected) { assertThat(targets()).containsExactly(expected); }

    private static final class CapturingExecution implements CacheInvalidationExecutionPort {
        final List<CacheInvalidationRequest> requests = new ArrayList<>();
        void clear() { requests.clear(); }
        @Override public void requestAndExecute(CacheInvalidationRequest request) { requests.add(request); }
        @Override public void executeInitial(CacheInvalidationBatchId batchId) { }
        @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { }
        @Override public void resumeStranded(CacheInvalidationBatchId batchId) { }
    }
}

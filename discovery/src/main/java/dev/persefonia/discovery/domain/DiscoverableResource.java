package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class DiscoverableResource {
    private final DiscoverableResourceId id;
    private final DiscoverableResourceKey key;
    private final SourceEntityRef sourceRef;
    private final DiscoverableResourceType resourceType;
    private final RoutePurpose routePurpose;
    private final DiscoveryLanguage language;
    private final PublicUrl publicUrl;
    private final CanonicalUrl canonicalUrl;
    private final ResourceTitle title;
    private final ResourceSummary summary;
    private final IndexingPolicy indexingPolicy;
    private final DiscoveryEligibility searchEligibility;
    private final DiscoveryEligibility sitemapEligibility;
    private final DiscoveryEligibility feedEligibility;
    private final SocialPreviewProfile openGraph;
    private final Instant publishedAt;
    private final Instant sourceUpdatedAt;
    private final SearchText searchText;
    private final Instant createdAt;
    private final Version version;

    private DiscoverableResource(
            DiscoverableResourceId id,
            DiscoverableResourceKey key,
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            ResourceTitle title,
            ResourceSummary summary,
            IndexingPolicy indexingPolicy,
            DiscoveryEligibility searchEligibility,
            DiscoveryEligibility sitemapEligibility,
            DiscoveryEligibility feedEligibility,
            SocialPreviewProfile openGraph,
            Instant publishedAt,
            Instant sourceUpdatedAt,
            SearchText searchText,
            Instant createdAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.key = Objects.requireNonNull(key, "key");
        this.sourceRef = key.sourceRef();
        this.resourceType = key.resourceType();
        this.routePurpose = key.routePurpose();
        this.language = key.language();
        this.publicUrl = Objects.requireNonNull(publicUrl, "publicUrl");
        this.canonicalUrl = Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        this.title = Objects.requireNonNull(title, "title");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.indexingPolicy = Objects.requireNonNull(indexingPolicy, "indexingPolicy");
        this.searchEligibility = Objects.requireNonNull(searchEligibility, "searchEligibility");
        this.sitemapEligibility = Objects.requireNonNull(sitemapEligibility, "sitemapEligibility");
        this.feedEligibility = Objects.requireNonNull(feedEligibility, "feedEligibility");
        this.openGraph = Objects.requireNonNull(openGraph, "openGraph");
        this.publishedAt = publishedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.searchText = Objects.requireNonNull(searchText, "searchText");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.version = Objects.requireNonNull(version, "version");
    }

    public static DiscoverableResource createCurrent(
            DiscoverableResourceId id,
            DiscoverableResourceKey key,
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            ResourceTitle title,
            ResourceSummary summary,
            IndexingPolicy indexingPolicy,
            DiscoveryEligibility searchEligibility,
            DiscoveryEligibility sitemapEligibility,
            DiscoveryEligibility feedEligibility,
            SocialPreviewProfile openGraph,
            Instant publishedAt,
            Instant sourceUpdatedAt,
            SearchText searchText,
            Instant createdAt,
            Version version) {
        return new DiscoverableResource(
                id, key, publicUrl, canonicalUrl, title, summary, indexingPolicy, searchEligibility,
                sitemapEligibility, feedEligibility, openGraph, publishedAt, sourceUpdatedAt, searchText, createdAt, version);
    }

    public DiscoverableResource replaceCurrentProjection(
            PublicUrl publicUrl,
            CanonicalUrl canonicalUrl,
            ResourceTitle title,
            ResourceSummary summary,
            IndexingPolicy indexingPolicy,
            DiscoveryEligibility searchEligibility,
            DiscoveryEligibility sitemapEligibility,
            DiscoveryEligibility feedEligibility,
            SocialPreviewProfile openGraph,
            Instant publishedAt,
            Instant sourceUpdatedAt,
            SearchText searchText) {
        return new DiscoverableResource(
                id, key, publicUrl, canonicalUrl, title, summary, indexingPolicy, searchEligibility,
                sitemapEligibility, feedEligibility, openGraph, publishedAt, sourceUpdatedAt, searchText, createdAt,
                version.next());
    }

    public DiscoverableResourceId id() {
        return id;
    }

    public DiscoverableResourceKey key() {
        return key;
    }

    public SourceEntityRef sourceRef() {
        return sourceRef;
    }

    public DiscoverableResourceType resourceType() {
        return resourceType;
    }

    public RoutePurpose routePurpose() {
        return routePurpose;
    }

    public DiscoveryLanguage language() {
        return language;
    }

    public PublicUrl publicUrl() {
        return publicUrl;
    }

    public CanonicalUrl canonicalUrl() {
        return canonicalUrl;
    }

    public ResourceTitle title() {
        return title;
    }

    public ResourceSummary summary() {
        return summary;
    }

    public IndexingPolicy indexingPolicy() {
        return indexingPolicy;
    }

    public DiscoveryEligibility searchEligibility() {
        return searchEligibility;
    }

    public DiscoveryEligibility sitemapEligibility() {
        return sitemapEligibility;
    }

    public DiscoveryEligibility feedEligibility() {
        return feedEligibility;
    }

    public SocialPreviewProfile openGraph() {
        return openGraph;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public Optional<Instant> sourceUpdatedAt() {
        return Optional.ofNullable(sourceUpdatedAt);
    }

    public SearchText searchText() {
        return searchText;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Version version() {
        return version;
    }
}

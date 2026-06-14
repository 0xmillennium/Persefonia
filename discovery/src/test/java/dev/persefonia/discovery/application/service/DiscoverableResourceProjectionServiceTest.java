package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.discovery.domain.DiscoverableResource;
import dev.persefonia.discovery.domain.DiscoverableResourceId;
import dev.persefonia.discovery.domain.DiscoverableResourceKey;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoverableResourceProjectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-10T08:00:00Z");
    private static final Instant SOURCE_UPDATED_AT = Instant.parse("2026-06-13T08:00:00Z");
    private static final SourceEntityId SOURCE_ID =
            new SourceEntityId(UUID.fromString("5b91a38c-bddc-439b-b89a-5c42231b62ad"));
    private static final UUID IMAGE_ID = UUID.fromString("35cf349b-d7f6-40dc-8829-4f20a30f4f86");

    @Test
    void updateCreatesAndReplacesCurrentProjectionWithAllInputMetadata() {
        InMemoryDiscoverableResourceRepository repository = new InMemoryDiscoverableResourceRepository();
        DiscoverableResourceProjectionService service = service(repository);

        DiscoverableResourceProjectionResult result = service.update(input());

        assertThat(result).isInstanceOf(DiscoverableResourceProjectionResult.Updated.class);
        assertThat(repository.replaced).isNotNull();
        DiscoverableResource resource = repository.replaced;
        assertThat(resource.key()).isEqualTo(new DiscoverableResourceKey(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                SOURCE_ID,
                DiscoverableResourceType.ARTICLE,
                DiscoveryLanguage.EN,
                RoutePurpose.DETAIL));
        assertThat(resource.sourceRef()).isEqualTo(sourceRef());
        assertThat(resource.publicUrl()).isEqualTo(new PublicUrl("/articles/current"));
        assertThat(resource.canonicalUrl()).isEqualTo(new CanonicalUrl("https://example.test/articles/current"));
        assertThat(resource.title().value()).isEqualTo("Current title");
        assertThat(resource.summary().value()).isEqualTo("Current summary");
        assertThat(resource.searchText().value()).isEqualTo("Current searchable text");
        assertThat(resource.indexingPolicy()).isEqualTo(IndexingPolicy.INDEX);
        assertThat(resource.searchEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
        assertThat(resource.sitemapEligibility()).isEqualTo(DiscoveryEligibility.ELIGIBLE);
        assertThat(resource.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        assertThat(resource.openGraph().title().value()).isEqualTo("Social title");
        assertThat(resource.openGraph().description().value()).isEqualTo("Social description");
        assertThat(resource.openGraph().imageAssetId()).isEqualTo(IMAGE_ID);
        assertThat(resource.publishedAt()).contains(PUBLISHED_AT);
        assertThat(resource.sourceUpdatedAt()).contains(SOURCE_UPDATED_AT);
        assertThat(resource.createdAt()).isEqualTo(NOW);
        assertThat(resource.version()).isEqualTo(Version.initial());
    }

    @Test
    void updateMapsAbsentOpenGraphMetadataToEmptyProfile() {
        InMemoryDiscoverableResourceRepository repository = new InMemoryDiscoverableResourceRepository();
        DiscoverableResourceProjectionInput input = inputWithOpenGraph(null, null, null);

        service(repository).update(input);

        assertThat(repository.replaced.openGraph().title()).isNull();
        assertThat(repository.replaced.openGraph().description()).isNull();
        assertThat(repository.replaced.openGraph().imageAssetId()).isNull();
    }

    @Test
    void removeUsesSourceReferenceAndIsIdempotent() {
        InMemoryDiscoverableResourceRepository repository = new InMemoryDiscoverableResourceRepository();
        repository.removeCount = 2;
        DiscoverableResourceProjectionService service = service(repository);
        RemoveDiscoverableResourceCommand command =
                new RemoveDiscoverableResourceCommand(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, SOURCE_ID);

        assertThat(service.remove(command)).isInstanceOf(DiscoverableResourceProjectionResult.Removed.class);
        assertThat(repository.removedSourceRef).isEqualTo(sourceRef());

        repository.removeCount = 0;
        assertThat(service.remove(command)).isInstanceOf(DiscoverableResourceProjectionResult.Noop.class);
        assertThat(service.remove(command)).isInstanceOf(DiscoverableResourceProjectionResult.Noop.class);
    }

    @Test
    void rejectsNullInputsAsProgrammingErrors() {
        DiscoverableResourceProjectionService service = service(new InMemoryDiscoverableResourceRepository());

        assertThatThrownBy(() -> service.update(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.remove(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DiscoverableResourceProjectionService service(DiscoverableResourceRepository repository) {
        return new DiscoverableResourceProjectionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DiscoverableResourceProjectionInput input() {
        return inputWithOpenGraph("Social title", "Social description", IMAGE_ID);
    }

    private static DiscoverableResourceProjectionInput inputWithOpenGraph(
            String openGraphTitle, String openGraphDescription, UUID openGraphImageAssetId) {
        return new DiscoverableResourceProjectionInput(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                SOURCE_ID,
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.EN,
                new PublicUrl("/articles/current"),
                new CanonicalUrl("https://example.test/articles/current"),
                "Current title",
                "Current summary",
                IndexingPolicy.INDEX,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.ELIGIBLE,
                DiscoveryEligibility.NOT_ELIGIBLE,
                openGraphTitle,
                openGraphDescription,
                openGraphImageAssetId,
                PUBLISHED_AT,
                SOURCE_UPDATED_AT,
                "Current searchable text");
    }

    private static SourceEntityRef sourceRef() {
        return new SourceEntityRef(SourceContext.CONTENT_PUBLISHING, SourceType.CONTENT_ITEM, SOURCE_ID);
    }

    private static final class InMemoryDiscoverableResourceRepository implements DiscoverableResourceRepository {
        private DiscoverableResource replaced;
        private SourceEntityRef removedSourceRef;
        private int removeCount;

        @Override
        public DiscoverableResource save(DiscoverableResource resource) {
            return resource;
        }

        @Override
        public DiscoverableResource replaceByKey(DiscoverableResource resource) {
            replaced = resource;
            return resource;
        }

        @Override
        public Optional<DiscoverableResource> findById(DiscoverableResourceId id) {
            return Optional.empty();
        }

        @Override
        public Optional<DiscoverableResource> findByKey(DiscoverableResourceKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<DiscoverableResource> findByPublicUrl(PublicUrl publicUrl) {
            return Optional.empty();
        }

        @Override
        public List<DiscoverableResource> findBySourceRef(SourceEntityRef sourceRef) {
            return List.of();
        }

        @Override
        public int removeBySourceRef(SourceEntityRef sourceRef) {
            removedSourceRef = sourceRef;
            return removeCount;
        }
    }
}

package dev.persefonia.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryEligibility;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.discovery.TagDiscoverabilityCoordinator;
import dev.persefonia.taxonomy.application.discovery.TagDiscoveryProjectionFactory;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagCommandProjectionTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final TaxonomyCommandActor OWNER = new TaxonomyCommandActor(UUID.randomUUID(), true, true);

    @Test
    void createUpdateAndArchiveSynchronizeCurrentTagPageProjectionsWithStablePolicy() {
        Tags tags = new Tags();
        RecordingUpdatePort updates = new RecordingUpdatePort();
        TagCommandService service = new TagCommandService(
                tags,
                new TagNormalizationService(),
                (actor, command) -> {},
                new TagDiscoverabilityCoordinator(updates, new TagDiscoveryProjectionFactory("https://example.test")));

        var created = service.create(new CreateTagCommand(OWNER, "Spring", "spring", "Framework", NOW));
        service.update(new UpdateTagCommand(
                OWNER, created.tagId(), "Spring Framework", "spring-framework", "Updated", NOW.plusSeconds(1)));
        service.archive(new ArchiveTagCommand(OWNER, created.tagId(), NOW.plusSeconds(2)));

        assertThat(updates.inputs).hasSize(6);
        assertThat(updates.inputs.subList(0, 2)).extracting(input -> input.publicUrl().value())
                .containsExactly("/tr/tags/spring", "/en/tags/spring");
        assertThat(updates.inputs.subList(2, 4)).extracting(input -> input.publicUrl().value())
                .containsExactly("/tr/tags/spring-framework", "/en/tags/spring-framework");
        assertThat(updates.inputs.subList(4, 6)).allSatisfy(input -> {
            assertThat(input.indexingPolicy()).isEqualTo(IndexingPolicy.NO_INDEX);
            assertThat(input.searchEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.sitemapEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
            assertThat(input.feedEligibility()).isEqualTo(DiscoveryEligibility.NOT_ELIGIBLE);
        });
    }

    private static final class RecordingUpdatePort implements UpdateDiscoverableResourcePort {
        private final List<DiscoverableResourceProjectionInput> inputs = new ArrayList<>();

        @Override
        public DiscoverableResourceProjectionResult update(DiscoverableResourceProjectionInput input) {
            inputs.add(input);
            return new DiscoverableResourceProjectionResult.Updated();
        }
    }

    private static final class Tags implements TagRepository {
        private final Map<TagId, Tag> values = new LinkedHashMap<>();
        @Override public Tag save(Tag tag) { values.put(tag.id(), tag); return tag; }
        @Override public Optional<Tag> findById(TagId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<Tag> findBySlug(TagSlug slug) {
            return values.values().stream().filter(tag -> tag.slug().equals(slug)).findFirst();
        }
        @Override public Optional<Tag> findByNormalizedName(NormalizedTagName name) {
            return values.values().stream().filter(tag -> tag.normalizedName().equals(name)).findFirst();
        }
        @Override public boolean existsBySlug(TagSlug slug) { return findBySlug(slug).isPresent(); }
        @Override public boolean existsByNormalizedName(NormalizedTagName name) {
            return findByNormalizedName(name).isPresent();
        }
        @Override public List<Tag> findAllForAdmin() { return List.copyOf(values.values()); }
    }
}

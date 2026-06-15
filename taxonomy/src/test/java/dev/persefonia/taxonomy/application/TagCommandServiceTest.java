package dev.persefonia.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.exception.TagCommandRejectedException;
import dev.persefonia.taxonomy.application.exception.TagNotFoundException;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final TaxonomyCommandActor OWNER = new TaxonomyCommandActor(UUID.randomUUID(), true, true);
    private static final TaxonomyCommandActor EDITOR = new TaxonomyCommandActor(UUID.randomUUID(), true, false);

    private final InMemoryTags tags = new InMemoryTags();
    private final TagCommandService service = new TagCommandService(tags, new TagNormalizationService(), ownerPolicy());

    @Test
    void ownerCanCreateUpdateAndArchiveTag() {
        var created = service.create(new CreateTagCommand(OWNER, "Java", "", "Language", NOW));
        service.update(new UpdateTagCommand(
                OWNER, created.tagId(), "Java Platform", "java-platform", "Runtime", NOW.plusSeconds(1)));
        var archived = service.archive(new ArchiveTagCommand(OWNER, created.tagId(), NOW.plusSeconds(2)));

        assertThat(tags.findById(created.tagId()).orElseThrow().name().value()).isEqualTo("Java Platform");
        assertThat(archived.status()).isEqualTo(TagStatus.ARCHIVED);
    }

    @Test
    void nonOwnerCannotMutateTags() {
        assertThatThrownBy(() -> service.create(new CreateTagCommand(EDITOR, "Java", "", null, NOW)))
                .isInstanceOf(SecurityException.class);

        var created = service.create(new CreateTagCommand(OWNER, "Java", "", null, NOW));
        assertThatThrownBy(() -> service.update(new UpdateTagCommand(
                EDITOR, created.tagId(), "JVM", "jvm", null, NOW.plusSeconds(1))))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.archive(new ArchiveTagCommand(EDITOR, created.tagId(), NOW.plusSeconds(1))))
                .isInstanceOf(SecurityException.class);

        assertThat(tags.findById(created.tagId()).orElseThrow().status()).isEqualTo(TagStatus.ACTIVE);
    }

    @Test
    void duplicateSlugAndNormalizedNameAreRejected() {
        service.create(new CreateTagCommand(OWNER, "Java", "java", null, NOW));

        assertThatThrownBy(() -> service.create(new CreateTagCommand(OWNER, "JVM", "java", null, NOW)))
                .isInstanceOf(TagCommandRejectedException.class)
                .extracting(exception -> ((TagCommandRejectedException) exception).reason())
                .isEqualTo(TagCommandRejectedException.Reason.DUPLICATE_SLUG);
        assertThatThrownBy(() -> service.create(new CreateTagCommand(OWNER, "  JAVA ", "other", null, NOW)))
                .isInstanceOf(TagCommandRejectedException.class)
                .extracting(exception -> ((TagCommandRejectedException) exception).reason())
                .isEqualTo(TagCommandRejectedException.Reason.DUPLICATE_NORMALIZED_NAME);
    }

    @Test
    void missingTagReturnsNotFound() {
        assertThatThrownBy(() -> service.archive(new ArchiveTagCommand(OWNER, TagId.newId(), NOW)))
                .isInstanceOf(TagNotFoundException.class);
    }

    private static TaxonomyCommandAuthorizationPolicy ownerPolicy() {
        return (actor, command) -> {
            if (!actor.active() || !actor.owner()) {
                throw new SecurityException("OWNER authorization required for " + command);
            }
        };
    }

    private static final class InMemoryTags implements TagRepository {
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
        @Override public boolean existsByNormalizedName(NormalizedTagName name) { return findByNormalizedName(name).isPresent(); }
        @Override public List<Tag> findAllForAdmin() { return List.copyOf(values.values()); }
    }
}

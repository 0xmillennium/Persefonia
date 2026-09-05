package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.RevisionType;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ContentRevisionPersistenceMapperTest {
    private final ContentRevisionPersistenceMapper mapper = new ContentRevisionPersistenceMapper();

    @Test
    void mapsDomainRevisionToPersistenceEntity() {
        var revision = ContentRevisionRepositoryTestFixtures.revision(
                ContentId.newId(), 1, RevisionType.PUBLISH, "mapper-revision");

        ContentRevisionPersistenceEntity entity = mapper.toEntity(revision);

        assertThat(entity.id()).isEqualTo(revision.id().value());
        assertThat(entity.contentItemId()).isEqualTo(revision.contentId().value());
        assertThat(entity.revisionNumber()).isEqualTo(1);
        assertThat(entity.revisionType()).isEqualTo("PUBLISH");
        assertThat(entity.title()).isEqualTo("Revision mapper-revision");
        assertThat(entity.slug()).isEqualTo("mapper-revision");
        assertThat(entity.renderedHtml()).contains("mapper-revision");
        assertThat(entity.createdByAdminRef()).isEqualTo(ContentRevisionRepositoryTestFixtures.ADMIN.value());
        assertThat(entity.changeNote()).isEqualTo("Change 1");
    }

    @Test
    void mapsPersistenceEntityToDomain() {
        UUID assetId = UUID.randomUUID();

        var revision = mapper.toDomain(entity("MANUAL_SNAPSHOT", assetId, null));

        assertThat(revision.revisionType()).isEqualTo(RevisionType.MANUAL_SNAPSHOT);
        assertThat(revision.metadata().canonicalPath().orElseThrow().value()).isEqualTo("/articles/mapper-revision");
        assertThat(revision.metadata().ogImageAssetId().orElseThrow().value()).isEqualTo(assetId);
        assertThat(revision.renderedHtml()).isEmpty();
        assertThat(revision.changeNote()).isEmpty();
    }

    @Test
    void invalidPersistedRevisionTypeFailsClearly() {
        assertThatThrownBy(() -> mapper.toDomain(entity("BROKEN", UUID.randomUUID(), "note")))
                .isInstanceOf(ContentPublishingPersistenceException.class)
                .hasMessageContaining("RevisionType");
    }

    private ContentRevisionPersistenceEntity entity(String revisionType, UUID assetId, String changeNote) {
        return new ContentRevisionPersistenceEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                revisionType,
                "Mapper Revision",
                "mapper-revision",
                "Mapper summary",
                "# Mapper",
                null,
                "Mapper SEO",
                "Mapper SEO description",
                "/articles/mapper-revision",
                "Mapper OG",
                "Mapper OG description",
                assetId,
                ContentRevisionRepositoryTestFixtures.ADMIN.value(),
                Instant.parse("2026-06-11T10:00:00Z"),
                changeNote);
    }
}

package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcContentTagAssignmentStoreTest extends ContentPublishingRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Autowired ContentTagAssignmentStore assignments;

    @Test
    void persistsUnknownTagIdsAndReplacesAssignments() {
        ContentId contentId = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("tag-store")).id();
        ReferencedTagId first = ReferencedTagId.from(UUID.randomUUID());
        ReferencedTagId second = ReferencedTagId.from(UUID.randomUUID());

        assignments.replaceAssignedTagIds(contentId, Set.of(first), NOW);
        assertThat(assignments.findAssignedTagIds(contentId)).containsExactly(first);

        assignments.replaceAssignedTagIds(contentId, Set.of(second), NOW.plusSeconds(1));
        assertThat(assignments.findAssignedTagIds(contentId)).containsExactly(second);
    }

    @Test
    void contentItemForeignKeyIsEnforced() {
        assertThatThrownBy(() -> assignments.replaceAssignedTagIds(
                        ContentId.newId(), Set.of(ReferencedTagId.from(UUID.randomUUID())), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

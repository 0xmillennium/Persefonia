package dev.persefonia.app.contentpublishing.persistence.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ContentRevisionJdbcAggregateSpikeTest extends ContentPublishingJdbcSpikeSupport {
    @Test
    void persistsContentRevisionAsSeparateAggregateRoot() {
        SpikeContentItem savedItem = adapter.saveContentItem(publishedItem("revision-root"));
        UUID adminRef = UUID.randomUUID();
        SpikeContentRevision first = revision(savedItem.id(), 1, adminRef)
                .withRenderedHtml("<article>First</article>")
                .withOgImageAssetId(UUID.randomUUID());
        SpikeContentRevision second = revision(savedItem.id(), 2, adminRef)
                .withTitle("Second title")
                .withChangeNote("Updated title");

        adapter.insertRevision(first);
        adapter.insertRevision(second);

        assertThat(adapter.findRevision(first.id()).orElseThrow()).isEqualTo(first);
        assertThat(adapter.findRevisionsByContentItemId(savedItem.id()))
                .extracting(SpikeContentRevision::revisionNumber)
                .containsExactly(1, 2);
        assertThat(adapter.findRevisionsByContentItemId(savedItem.id()).getFirst())
                .usingRecursiveComparison()
                .isEqualTo(first);
        assertThat(adapter.findContentItem(savedItem.id()).orElseThrow().renderSnapshot()).isNull();
    }

    @Test
    void rejectsDuplicateRevisionNumber() {
        SpikeContentItem savedItem = adapter.saveContentItem(publishedItem("duplicate-revision"));
        UUID adminRef = UUID.randomUUID();
        adapter.insertRevision(revision(savedItem.id(), 1, adminRef));

        assertThatThrownBy(() -> adapter.insertRevision(revision(savedItem.id(), 1, adminRef)))
                .isInstanceOf(DataAccessException.class);
    }
}

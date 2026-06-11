package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ContentItemPersistenceMapperTest {
    private final ContentItemPersistenceMapper mapper = new ContentItemPersistenceMapper();

    @Test
    void mapsDomainRootToPersistenceEntity() {
        var item = ContentItemRepositoryTestFixtures.published(
                "mapper-root",
                dev.persefonia.contentpublishing.domain.content.ContentVisibility.PUBLIC);

        ContentItemPersistenceEntity entity = mapper.toEntity(item, true);

        assertThat(entity.id()).isEqualTo(item.id().value());
        assertThat(entity.type()).isEqualTo("ARTICLE");
        assertThat(entity.status()).isEqualTo("PUBLISHED");
        assertThat(entity.visibility()).isEqualTo("PUBLIC");
        assertThat(entity.language()).isEqualTo("EN");
        assertThat(entity.slug()).isEqualTo("mapper-root");
        assertThat(entity.title()).isEqualTo("Title mapper-root");
        assertThat(entity.summary()).isEqualTo("Summary for mapper-root");
        assertThat(entity.markdownSource()).isEqualTo("# mapper-root");
        assertThat(entity.metaTitle()).isEqualTo("SEO mapper-root");
        assertThat(entity.metaDescription()).isEqualTo("SEO description mapper-root");
        assertThat(entity.canonicalPath()).isEqualTo("/articles/mapper-root");
        assertThat(entity.ogTitle()).isEqualTo("OG mapper-root");
        assertThat(entity.ogDescription()).isEqualTo("OG description mapper-root");
        assertThat(entity.ogImageAssetId()).isInstanceOf(UUID.class);
        assertThat(entity.version()).isNull();
    }

    @Test
    void mapsPersistenceRowsToDomainIncludingSnapshotAndHeadings() {
        UUID assetId = UUID.randomUUID();
        ContentItemPersistenceEntity entity = entity("ARTICLE", "PUBLISHED", "PUBLIC", "EN", assetId);
        var snapshot = new ContentItemRenderSnapshotTable.Row(
                entity.id(), "<article>Mapper</article>", Instant.parse("2026-06-11T10:00:00Z"), "renderer-x", 5, true);
        var headings = List.of(
                new ContentItemRenderedHeadingTable.Row(UUID.randomUUID(), entity.id(), 1, "Intro", "intro", 1),
                new ContentItemRenderedHeadingTable.Row(UUID.randomUUID(), entity.id(), 2, "Details", "details", 2));

        var item = mapper.toDomain(entity, snapshot, headings);

        assertThat(item.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(item.metadata().ogImageAssetId()).contains(AssetId.from(assetId));
        assertThat(item.renderSnapshot().orElseThrow().rendererVersion().value()).isEqualTo("renderer-x");
        assertThat(item.renderSnapshot().orElseThrow().readingTime().minutes()).isEqualTo(5);
        assertThat(item.renderSnapshot().orElseThrow().containsMermaid()).isTrue();
        assertThat(item.renderSnapshot().orElseThrow().headings())
                .extracting(heading -> heading.anchor().value())
                .containsExactly("intro", "details");
    }

    @Test
    void mapsNullOptionalColumnsToAbsentDomainValues() {
        var item = mapper.toDomain(entity("ARTICLE", "DRAFT", "PRIVATE", "TR", null), null, List.of());

        assertThat(item.slug()).isEmpty();
        assertThat(item.metadata().seoTitle()).isEmpty();
        assertThat(item.metadata().ogImageAssetId()).isEmpty();
        assertThat(item.renderSnapshot()).isEmpty();
    }

    @Test
    void invalidPersistedEnumsFailClearly() {
        assertThatThrownBy(() -> mapper.toDomain(entity("BROKEN", "DRAFT", "PRIVATE", "EN", null), null, List.of()))
                .isInstanceOf(ContentPublishingPersistenceException.class)
                .hasMessageContaining("ContentType");
        assertThatThrownBy(() -> mapper.toDomain(entity("ARTICLE", "BROKEN", "PRIVATE", "EN", null), null, List.of()))
                .isInstanceOf(ContentPublishingPersistenceException.class)
                .hasMessageContaining("ContentStatus");
        assertThatThrownBy(() -> mapper.toDomain(entity("ARTICLE", "DRAFT", "BROKEN", "EN", null), null, List.of()))
                .isInstanceOf(ContentPublishingPersistenceException.class)
                .hasMessageContaining("ContentVisibility");
        assertThatThrownBy(() -> mapper.toDomain(entity("ARTICLE", "DRAFT", "PRIVATE", "FR", null), null, List.of()))
                .isInstanceOf(ContentPublishingPersistenceException.class)
                .hasMessageContaining("ContentLanguage");
    }

    private ContentItemPersistenceEntity entity(
            String type,
            String status,
            String visibility,
            String language,
            UUID assetId) {
        boolean complete = "PUBLISHED".equals(status);
        String slug = complete ? "mapper-row" : null;
        return new ContentItemPersistenceEntity(
                UUID.randomUUID(),
                type,
                status,
                visibility,
                language,
                slug,
                complete ? "Mapper Row" : null,
                complete ? "Mapper summary" : null,
                complete ? "# Mapper" : null,
                complete ? "Mapper SEO" : null,
                complete ? "Mapper SEO description" : null,
                complete ? "/articles/mapper-row" : null,
                complete ? "Mapper OG" : null,
                complete ? "Mapper OG description" : null,
                assetId,
                complete ? Instant.parse("2026-06-11T09:00:00Z") : null,
                null,
                Instant.parse("2026-06-11T08:00:00Z"),
                Instant.parse("2026-06-11T09:00:00Z"),
                3L);
    }
}

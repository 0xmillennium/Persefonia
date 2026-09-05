package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JdbcContentItemRepositoryAdapterRootTest extends ContentPublishingRepositoryTestDatabase {
    @Test
    void savesAndLoadsIncompleteDraftRoot() {
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.incompleteDraft());
        var loaded = contentItems.findById(saved.id()).orElseThrow();

        assertThat(saved.version().value()).isZero();
        assertThat(loaded.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(loaded.visibility()).isEqualTo(ContentVisibility.PRIVATE);
        assertThat(loaded.slug()).isEmpty();
        assertThat(loaded.title()).isEmpty();
        assertThat(loaded.summary()).isEmpty();
        assertThat(loaded.markdownSource()).isEmpty();
        assertThat(loaded.tagIds()).isEmpty();
    }

    @Test
    void savesAndLoadsScalarFieldsAndMetadata() {
        var saved = contentItems.save(ContentItemRepositoryTestFixtures.completeDraft("root-round-trip"));
        var loaded = contentItems.findById(saved.id()).orElseThrow();

        assertThat(loaded.type()).isEqualTo(ContentType.ARTICLE);
        assertThat(loaded.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(loaded.visibility()).isEqualTo(ContentVisibility.PUBLIC);
        assertThat(loaded.language()).isEqualTo(ContentLanguage.EN);
        assertThat(loaded.slug().orElseThrow().value()).isEqualTo("root-round-trip");
        assertThat(loaded.title().orElseThrow().value()).isEqualTo("Title root-round-trip");
        assertThat(loaded.summary().orElseThrow().value()).isEqualTo("Summary for root-round-trip");
        assertThat(loaded.markdownSource().orElseThrow().value()).isEqualTo("# root-round-trip");
        assertThat(loaded.metadata().seoTitle().orElseThrow().value()).isEqualTo("SEO root-round-trip");
        assertThat(loaded.metadata().seoDescription().orElseThrow().value()).isEqualTo("SEO description root-round-trip");
        assertThat(loaded.metadata().canonicalPath().orElseThrow().value()).isEqualTo("/articles/root-round-trip");
        assertThat(loaded.metadata().openGraphTitle().orElseThrow().value()).isEqualTo("OG root-round-trip");
        assertThat(loaded.metadata().openGraphDescription().orElseThrow().value()).isEqualTo("OG description root-round-trip");
        assertThat(loaded.metadata().ogImageAssetId()).containsInstanceOf(AssetId.class);
        assertThat(loaded.version().value()).isZero();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(contentItems.findById(ContentId.from(UUID.randomUUID()))).isEmpty();
    }
}

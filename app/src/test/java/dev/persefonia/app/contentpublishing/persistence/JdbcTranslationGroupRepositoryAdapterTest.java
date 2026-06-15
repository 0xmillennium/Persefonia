package dev.persefonia.app.contentpublishing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Version;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class JdbcTranslationGroupRepositoryAdapterTest extends ContentPublishingRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Autowired TranslationGroupRepository translationGroups;

    @Test
    void persistsAndLoadsTranslationGroupWithEntries() {
        ContentId english = saveContent("repo-en", ContentLanguage.EN);
        ContentId turkish = saveContent("repo-tr", ContentLanguage.TR);
        TranslationGroup group = TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                List.of(entry(english, ContentLanguage.EN), entry(turkish, ContentLanguage.TR)),
                NOW,
                NOW,
                Version.initial());

        TranslationGroupId id = translationGroups.save(group).id();

        TranslationGroup loaded = translationGroups.findById(id).orElseThrow();
        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.containsContentItem(english)).isTrue();
        assertThat(loaded.containsContentItem(turkish)).isTrue();
    }

    @Test
    void findsGroupByContentItemId() {
        ContentId english = saveContent("find-en", ContentLanguage.EN);
        TranslationGroupId id = translationGroups.save(group(english)).id();

        assertThat(translationGroups.findByContentItemId(english).orElseThrow().id()).isEqualTo(id);
        assertThat(translationGroups.contentItemBelongsToAnyGroup(english)).isTrue();
    }

    @Test
    void uniqueContentMembershipEnforced() {
        ContentId english = saveContent("member-en", ContentLanguage.EN);
        translationGroups.save(group(english));

        TranslationGroup second = TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                List.of(entry(english, ContentLanguage.EN)),
                NOW, NOW, Version.initial());

        assertThatThrownBy(() -> translationGroups.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueLanguagePerGroupEnforced() {
        ContentId first = saveContent("lang-one", ContentLanguage.EN);
        ContentId second = saveContent("lang-two", ContentLanguage.EN);
        TranslationGroup group = TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                List.of(entry(first, ContentLanguage.EN), entry(second, ContentLanguage.EN)),
                NOW, NOW, Version.initial());

        assertThatThrownBy(() -> translationGroups.save(group))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticVersionHandledAccordingToProjectPattern() {
        ContentId english = saveContent("opt-en", ContentLanguage.EN);
        ContentId turkish = saveContent("opt-tr", ContentLanguage.TR);
        TranslationGroupId id = translationGroups.save(group(english)).id();

        TranslationGroup current = translationGroups.findById(id).orElseThrow();
        TranslationGroup stale = translationGroups.findById(id).orElseThrow();

        current.addEntry(entry(turkish, ContentLanguage.TR), NOW.plusSeconds(1));
        translationGroups.save(current);

        ContentId french = saveContent("opt-fr", ContentLanguage.TR);
        assertThatThrownBy(() -> {
            stale.addEntry(new TranslationGroupEntry(
                    TranslationGroupEntryId.newId(), french, ContentLanguage.TR, ContentType.ARTICLE, NOW),
                    NOW.plusSeconds(2));
            translationGroups.save(stale);
        }).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private ContentId saveContent(String slug, ContentLanguage language) {
        return contentItems.save(
                ContentItemRepositoryTestFixtures.completeDraft(slug, ContentType.ARTICLE, language)).id();
    }

    private TranslationGroup group(ContentId contentId) {
        return TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                List.of(entry(contentId, ContentLanguage.EN)),
                NOW, NOW, Version.initial());
    }

    private static TranslationGroupEntry entry(ContentId contentId, ContentLanguage language) {
        return new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), contentId, language, ContentType.ARTICLE, NOW);
    }
}

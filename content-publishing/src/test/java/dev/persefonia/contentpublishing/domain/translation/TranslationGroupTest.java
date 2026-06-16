package dev.persefonia.contentpublishing.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TranslationGroupTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-15T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-15T09:00:00Z");

    @Test
    void createsGroupWithInitialEntry() {
        TranslationGroupEntry entry = entry(ContentLanguage.EN, ContentType.ARTICLE);
        TranslationGroup group = TranslationGroup.create(TranslationGroupId.newId(), entry, CREATED_AT);

        assertThat(group.entries()).containsExactly(entry);
        assertThat(group.contentType()).isEqualTo(ContentType.ARTICLE);
        assertThat(group.version().value()).isZero();
        assertThat(group.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsDuplicateContentItemEntry() {
        ContentId contentId = ContentId.newId();
        TranslationGroupEntry entry = new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), contentId, ContentLanguage.EN, ContentType.ARTICLE, CREATED_AT);
        TranslationGroup group = TranslationGroup.create(TranslationGroupId.newId(), entry, CREATED_AT);
        TranslationGroupEntry duplicate = new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), contentId, ContentLanguage.TR, ContentType.ARTICLE, LATER);

        assertThatThrownBy(() -> group.addEntry(duplicate, LATER))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void rejectsDuplicateLanguageEntry() {
        TranslationGroup group = TranslationGroup.create(
                TranslationGroupId.newId(), entry(ContentLanguage.EN, ContentType.ARTICLE), CREATED_AT);

        assertThatThrownBy(() -> group.addEntry(entry(ContentLanguage.EN, ContentType.ARTICLE), LATER))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void addsEntryWithNewLanguage() {
        TranslationGroup group = TranslationGroup.create(
                TranslationGroupId.newId(), entry(ContentLanguage.EN, ContentType.ARTICLE), CREATED_AT);
        TranslationGroupEntry turkish = entry(ContentLanguage.TR, ContentType.ARTICLE);

        group.addEntry(turkish, LATER);

        assertThat(group.entries()).hasSize(2);
        assertThat(group.containsLanguage(ContentLanguage.TR)).isTrue();
    }

    @Test
    void rejectsEntryWithDifferentContentTypeIfModeled() {
        TranslationGroup group = TranslationGroup.create(
                TranslationGroupId.newId(), entry(ContentLanguage.EN, ContentType.ARTICLE), CREATED_AT);

        assertThatThrownBy(() -> group.addEntry(entry(ContentLanguage.TR, ContentType.NOTE), LATER))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void rehydrateRejectsEmptyEntries() {
        assertThatThrownBy(() -> TranslationGroup.rehydrate(
                        TranslationGroupId.newId(), java.util.List.of(), CREATED_AT, CREATED_AT, dev.persefonia.contentpublishing.domain.content.Version.initial()))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void rehydrateRejectsDuplicateContentItem() {
        ContentId contentId = ContentId.newId();
        TranslationGroupEntry english = new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), contentId, ContentLanguage.EN, ContentType.ARTICLE, CREATED_AT);
        TranslationGroupEntry turkish = new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), contentId, ContentLanguage.TR, ContentType.ARTICLE, CREATED_AT);

        assertThatThrownBy(() -> TranslationGroup.rehydrate(
                        TranslationGroupId.newId(), java.util.List.of(english, turkish), CREATED_AT, CREATED_AT,
                        dev.persefonia.contentpublishing.domain.content.Version.initial()))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void rehydrateRejectsDuplicateLanguage() {
        assertThatThrownBy(() -> TranslationGroup.rehydrate(
                        TranslationGroupId.newId(),
                        java.util.List.of(
                                entry(ContentLanguage.EN, ContentType.ARTICLE),
                                entry(ContentLanguage.EN, ContentType.ARTICLE)),
                        CREATED_AT,
                        CREATED_AT,
                        dev.persefonia.contentpublishing.domain.content.Version.initial()))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void rehydrateRejectsMixedContentTypes() {
        assertThatThrownBy(() -> TranslationGroup.rehydrate(
                        TranslationGroupId.newId(),
                        java.util.List.of(
                                entry(ContentLanguage.EN, ContentType.ARTICLE),
                                entry(ContentLanguage.TR, ContentType.NOTE)),
                        CREATED_AT,
                        CREATED_AT,
                        dev.persefonia.contentpublishing.domain.content.Version.initial()))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void removesEntryWhenMoreThanOneEntryExists() {
        TranslationGroup group = TranslationGroup.create(
                TranslationGroupId.newId(), entry(ContentLanguage.EN, ContentType.ARTICLE), CREATED_AT);
        TranslationGroupEntry turkish = entry(ContentLanguage.TR, ContentType.ARTICLE);
        group.addEntry(turkish, LATER);

        group.removeEntry(turkish.id(), LATER);

        assertThat(group.entries()).hasSize(1);
        assertThat(group.containsLanguage(ContentLanguage.TR)).isFalse();
    }

    @Test
    void rejectsRemovingLastEntry() {
        TranslationGroupEntry only = entry(ContentLanguage.EN, ContentType.ARTICLE);
        TranslationGroup group = TranslationGroup.create(TranslationGroupId.newId(), only, CREATED_AT);

        assertThatThrownBy(() -> group.removeEntry(only.id(), LATER))
                .isInstanceOf(TranslationGroupValidationException.class);
    }

    @Test
    void updatesTimestampWhenEntryChanges() {
        TranslationGroup group = TranslationGroup.create(
                TranslationGroupId.newId(), entry(ContentLanguage.EN, ContentType.ARTICLE), CREATED_AT);

        group.addEntry(entry(ContentLanguage.TR, ContentType.ARTICLE), LATER);

        assertThat(group.updatedAt()).isEqualTo(LATER);
        assertThat(group.version().value()).isEqualTo(1);
    }

    private static TranslationGroupEntry entry(ContentLanguage language, ContentType type) {
        return new TranslationGroupEntry(
                TranslationGroupEntryId.newId(), ContentId.newId(), language, type, CREATED_AT);
    }
}

package dev.persefonia.app.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import dev.persefonia.taxonomy.application.query.TagVocabularyItem;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaxonomyContentTagVocabularyAdapterTest {
    private final TagVocabularyQueryService queries = mock(TagVocabularyQueryService.class);
    private final TaxonomyContentTagVocabularyAdapter adapter = new TaxonomyContentTagVocabularyAdapter(queries);

    @Test
    void mapsAssignableAndAssignedTagReadModelsWithoutLeakingTaxonomyTypes() {
        TagVocabularyItem active = item(TagStatus.ACTIVE);
        TagVocabularyItem archived = item(TagStatus.ARCHIVED);
        when(queries.findAssignableTags()).thenReturn(List.of(active));
        when(queries.findByIds(Set.of(active.id(), archived.id()))).thenReturn(List.of(active, archived));

        assertThat(adapter.findAssignableTags()).extracting(option -> option.id().value())
                .containsExactly(active.id().value());
        assertThat(adapter.findByIds(Set.of(reference(active), reference(archived))))
                .extracting(details -> details.archived())
                .containsExactly(false, true);
    }

    @Test
    void validationRejectsMissingAndNewArchivedButAllowsExistingArchived() {
        TagVocabularyItem archived = item(TagStatus.ARCHIVED);
        ReferencedTagId archivedReference = reference(archived);
        ReferencedTagId missing = ReferencedTagId.from(java.util.UUID.randomUUID());
        when(queries.findByIds(Set.of(archived.id(), TagId.from(missing.value())))).thenReturn(List.of(archived));

        var rejected = adapter.validateAssignments(Set.of(), Set.of(archivedReference, missing));
        assertThat(rejected.missingTagIds()).containsExactly(missing);
        assertThat(rejected.newlyArchivedTagIds()).containsExactly(archivedReference);

        when(queries.findByIds(Set.of(archived.id()))).thenReturn(List.of(archived));
        assertThat(adapter.validateAssignments(Set.of(archivedReference), Set.of(archivedReference)).valid()).isTrue();
    }

    private static TagVocabularyItem item(TagStatus status) {
        return new TagVocabularyItem(TagId.newId(), status.name(), status.name().toLowerCase(), status);
    }

    private static ReferencedTagId reference(TagVocabularyItem item) {
        return ReferencedTagId.from(item.id().value());
    }
}

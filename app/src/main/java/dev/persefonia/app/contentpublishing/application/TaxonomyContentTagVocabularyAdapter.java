package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.application.port.TagAssignmentValidation;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.taxonomy.application.query.TagVocabularyItem;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class TaxonomyContentTagVocabularyAdapter implements ContentTagVocabularyPort {
    private final TagVocabularyQueryService vocabulary;

    TaxonomyContentTagVocabularyAdapter(TagVocabularyQueryService vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    @Override
    public List<AssignableTagOption> findAssignableTags() {
        return vocabulary.findAssignableTags().stream()
                .map(item -> new AssignableTagOption(reference(item.id()), item.name(), item.slug()))
                .toList();
    }

    @Override
    public List<ReferencedTagDetails> findByIds(Set<TagId> ids) {
        return vocabulary.findByIds(taxonomyIds(ids)).stream()
                .map(TaxonomyContentTagVocabularyAdapter::details)
                .toList();
    }

    @Override
    public TagAssignmentValidation validateAssignments(
            Set<TagId> currentlyAssigned,
            Set<TagId> requested) {
        Map<TagId, TagVocabularyItem> found = vocabulary.findByIds(taxonomyIds(requested)).stream()
                .collect(Collectors.toMap(item -> reference(item.id()), Function.identity()));

        Set<TagId> missing = new LinkedHashSet<>(requested);
        missing.removeAll(found.keySet());

        Set<TagId> newlyArchived = found.entrySet().stream()
                .filter(entry -> entry.getValue().status() == TagStatus.ARCHIVED)
                .map(entry -> entry.getKey())
                .filter(id -> !currentlyAssigned.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new TagAssignmentValidation(missing, newlyArchived);
    }

    private static Set<dev.persefonia.taxonomy.domain.model.TagId> taxonomyIds(Set<TagId> ids) {
        return ids.stream()
                .map(id -> dev.persefonia.taxonomy.domain.model.TagId.from(id.value()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static ReferencedTagDetails details(TagVocabularyItem item) {
        return new ReferencedTagDetails(
                reference(item.id()), item.name(), item.slug(), item.status() == TagStatus.ARCHIVED);
    }

    private static TagId reference(dev.persefonia.taxonomy.domain.model.TagId id) {
        return TagId.from(id.value());
    }
}

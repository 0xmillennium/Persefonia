package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.port.ProjectTagAssignmentValidation;
import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.taxonomy.application.query.TagVocabularyItem;
import dev.persefonia.taxonomy.application.service.TagVocabularyQueryService;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class TaxonomyProjectTagVocabularyAdapter implements ProjectTagVocabularyPort {
    private final TagVocabularyQueryService vocabulary;

    TaxonomyProjectTagVocabularyAdapter(TagVocabularyQueryService vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    @Override
    public java.util.List<ProjectTagOption> findAssignableTags() {
        return vocabulary.findAssignableTags().stream()
                .map(item -> new ProjectTagOption(item.id().value(), item.name(), item.slug()))
                .toList();
    }

    @Override
    public java.util.List<ProjectTagDetails> findByIds(Set<TagId> ids) {
        return vocabulary.findByIds(taxonomyIds(ids)).stream()
                .map(item -> new ProjectTagDetails(
                        item.id().value(), item.name(), item.slug(), item.status() == TagStatus.ARCHIVED))
                .toList();
    }

    @Override
    public ProjectTagAssignmentValidation validateAssignments(Set<TagId> currentlyAssigned, Set<TagId> requested) {
        Map<TagId, TagVocabularyItem> found = vocabulary.findByIds(taxonomyIds(requested)).stream()
                .collect(Collectors.toMap(item -> reference(item.id()), Function.identity()));

        Set<TagId> missing = new LinkedHashSet<>(requested);
        missing.removeAll(found.keySet());

        Set<TagId> newlyArchived = found.entrySet().stream()
                .filter(entry -> entry.getValue().status() == TagStatus.ARCHIVED)
                .map(Map.Entry::getKey)
                .filter(id -> !currentlyAssigned.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new ProjectTagAssignmentValidation(missing, newlyArchived);
    }

    private static Set<dev.persefonia.taxonomy.domain.model.TagId> taxonomyIds(Set<TagId> ids) {
        return ids.stream()
                .map(id -> dev.persefonia.taxonomy.domain.model.TagId.from(id.value()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static TagId reference(dev.persefonia.taxonomy.domain.model.TagId id) {
        return TagId.from(id.value());
    }
}

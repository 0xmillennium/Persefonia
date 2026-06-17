package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.port.ProjectTagDetails;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.PublicProjectTagView;
import dev.persefonia.profileportfolio.domain.common.TagId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ProjectPublicTagMapper {
    private final ProjectTagVocabularyPort vocabulary;

    ProjectPublicTagMapper(ProjectTagVocabularyPort vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    List<PublicProjectTagView> activeTags(Set<TagId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return vocabulary.findByIds(ids).stream()
                .filter(tag -> !tag.archived())
                .sorted(Comparator.comparing(ProjectTagDetails::name).thenComparing(ProjectTagDetails::slug))
                .map(tag -> new PublicProjectTagView(tag.name(), tag.slug()))
                .toList();
    }
}

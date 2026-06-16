package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public record ProjectLocalization(
        ProjectLocalizationId id,
        ContentLanguage language,
        ProjectSlug slug,
        ProjectTitle title,
        ProjectSummary summary,
        List<ProjectCaseStudySection> sections) {
    public ProjectLocalization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        rejectDuplicate(sections, ProjectCaseStudySection::type, "case study section type");
        rejectDuplicate(sections, ProjectCaseStudySection::sortOrder, "case study section sort order");
    }

    private static <T, K> void rejectDuplicate(List<T> values, Function<T, K> key, String label) {
        Set<K> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(key.apply(value))) {
                throw new ProjectValidationException("duplicate " + label);
            }
        }
    }
}

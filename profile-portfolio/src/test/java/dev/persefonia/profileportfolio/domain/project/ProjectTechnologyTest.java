package dev.persefonia.profileportfolio.domain.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectTechnologyTest {
    @Test
    void rejectsDuplicateTechnologyNormalizedNameAndCategory() {
        assertThatThrownBy(() -> Project.create(
                ProjectId.newId(),
                ProjectStatus.ACTIVE,
                ProjectVisibility.PUBLIC,
                false,
                null,
                null,
                Set.of(),
                List.of(technology("java", TechnologyCategory.LANGUAGE, 1), technology("java", TechnologyCategory.LANGUAGE, 2)),
                List.of(),
                List.of(localization(ContentLanguage.TR)),
                ContentLanguage.TR,
                Instant.parse("2026-06-16T10:00:00Z")))
                .isInstanceOf(ProjectValidationException.class);
    }

    private static ProjectTechnology technology(String normalizedName, TechnologyCategory category, int sortOrder) {
        return new ProjectTechnology(
                ProjectTechnologyId.newId(),
                TechnologyName.of("Java"),
                NormalizedTechnologyName.of(normalizedName),
                category,
                SortOrder.of(sortOrder));
    }

    private static ProjectLocalization localization(ContentLanguage language) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                language,
                ProjectSlug.of("sample-project"),
                ProjectTitle.of("Sample Project"),
                ProjectSummary.of("Summary"),
                List.of());
    }
}

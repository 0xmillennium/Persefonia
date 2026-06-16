package dev.persefonia.profileportfolio.domain.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectLocalizationTest {
    @Test
    void rejectsDuplicateCaseStudySectionType() {
        assertThatThrownBy(() -> localization(List.of(section(CaseStudySectionType.PROBLEM, 1), section(CaseStudySectionType.PROBLEM, 2))))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsDuplicateCaseStudySectionSortOrder() {
        assertThatThrownBy(() -> localization(List.of(section(CaseStudySectionType.PROBLEM, 1), section(CaseStudySectionType.RESULT, 1))))
                .isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void rejectsInvalidProjectSlug() {
        assertThatThrownBy(() -> ProjectSlug.of("Invalid Slug"))
                .isInstanceOf(ProjectValidationException.class);
    }

    private static ProjectLocalization localization(List<ProjectCaseStudySection> sections) {
        return new ProjectLocalization(
                ProjectLocalizationId.newId(),
                ContentLanguage.TR,
                ProjectSlug.of("sample-project"),
                ProjectTitle.of("Sample Project"),
                ProjectSummary.of("Summary"),
                sections);
    }

    private static ProjectCaseStudySection section(CaseStudySectionType type, int sortOrder) {
        return new ProjectCaseStudySection(
                ProjectCaseStudySectionId.newId(),
                type,
                CaseStudyText.of("Body"),
                SortOrder.of(sortOrder));
    }
}

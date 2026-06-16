package dev.persefonia.profileportfolio.domain.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalProfileTest {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> DisplayName.of(" "))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateLocalizationLanguage() {
        assertThatThrownBy(() -> profile(List.of(localization(ContentLanguage.TR), localization(ContentLanguage.TR)), List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateExternalLinkSortOrder() {
        assertThatThrownBy(() -> profile(List.of(localization(ContentLanguage.TR)), List.of(link(1), link(1))))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateTechnicalFocusAreaSortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(focusArea(1), focusArea(1)),
                List.of(),
                List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateEducationSummarySortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(),
                List.of(education(1), education(1)),
                List.of()))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDuplicateCurrentFocusItemSortOrder() {
        assertThatThrownBy(() -> new ProfileLocalization(
                ProfileLocalizationId.newId(),
                ContentLanguage.TR,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                null,
                List.of(),
                List.of(),
                List.of(focusItem(1), focusItem(1))))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsInvalidExternalUrl() {
        assertThatThrownBy(() -> ExternalUrl.of("not a url"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    private static PersonalProfile profile(List<ProfileLocalization> localizations, List<ExternalProfileLink> links) {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                true,
                localizations,
                links,
                NOW);
    }

    private static ProfileLocalization localization(ContentLanguage language) {
        return new ProfileLocalization(
                ProfileLocalizationId.newId(),
                language,
                ShortBio.of("Short"),
                LongBio.of("Long"),
                LocationText.of("Istanbul"),
                List.of(),
                List.of(),
                List.of());
    }

    private static ExternalProfileLink link(int sortOrder) {
        return new ExternalProfileLink(
                ExternalProfileLinkId.newId(),
                LinkLabel.of("GitHub"),
                ExternalUrl.of("https://example.test"),
                SortOrder.of(sortOrder));
    }

    private static TechnicalFocusArea focusArea(int sortOrder) {
        return new TechnicalFocusArea(
                TechnicalFocusAreaId.newId(),
                FocusAreaName.of("Architecture"),
                null,
                SortOrder.of(sortOrder));
    }

    private static EducationSummary education(int sortOrder) {
        return new EducationSummary(
                EducationSummaryId.newId(),
                InstitutionName.of("University"),
                ProgramName.of("Computer Science"),
                null,
                SortOrder.of(sortOrder));
    }

    private static CurrentFocusItem focusItem(int sortOrder) {
        return new CurrentFocusItem(
                CurrentFocusItemId.newId(),
                FocusItemText.of("Build systems"),
                SortOrder.of(sortOrder));
    }
}

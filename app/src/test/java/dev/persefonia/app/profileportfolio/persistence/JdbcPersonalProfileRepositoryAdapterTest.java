package dev.persefonia.app.profileportfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItem;
import dev.persefonia.profileportfolio.domain.profile.CurrentFocusItemId;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.EducationSummary;
import dev.persefonia.profileportfolio.domain.profile.EducationSummaryId;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.profile.FocusAreaName;
import dev.persefonia.profileportfolio.domain.profile.FocusItemText;
import dev.persefonia.profileportfolio.domain.profile.InstitutionName;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ProgramName;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusArea;
import dev.persefonia.profileportfolio.domain.profile.TechnicalFocusAreaId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcPersonalProfileRepositoryAdapterTest extends PortfolioRepositoryTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Test
    void savesAndReloadsProfileWithLocalizationsAndChildCollections() {
        PersonalProfile saved = profiles.save(profile(true, localization(ContentLanguage.TR), List.of(link(1))));

        PersonalProfile reloaded = profiles.findById(saved.id()).orElseThrow();

        assertThat(reloaded.localizations()).hasSize(1);
        assertThat(reloaded.localizations().getFirst().technicalFocusAreas()).hasSize(1);
        assertThat(reloaded.localizations().getFirst().educationSummaries()).hasSize(1);
        assertThat(reloaded.localizations().getFirst().currentFocusItems()).hasSize(1);
        assertThat(reloaded.externalLinks()).hasSize(1);
        assertThat(profiles.findActiveProfile()).map(PersonalProfile::id).contains(saved.id());
    }

    @Test
    void updatesProfileChildCollectionsWithoutLeavingOrphans() {
        PersonalProfile saved = profiles.save(profile(false, localization(ContentLanguage.TR), List.of(link(1))));
        saved.updateActiveProfile(
                DisplayName.of("Updated"),
                List.of(localization(ContentLanguage.EN)),
                List.of(link(2)),
                NOW.plusSeconds(1));

        PersonalProfile updated = profiles.save(saved);

        assertThat(updated.displayName().value()).isEqualTo("Updated");
        assertThat(updated.externalLinks()).extracting(link -> link.sortOrder().value()).containsExactly(2);
        assertThat(updated.localizations()).extracting(ProfileLocalization::language).containsExactly(ContentLanguage.EN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.external_profile_links", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.profile_localizations", Long.class)).isEqualTo(1);
    }

    @Test
    void duplicateActiveProfileIsRejectedByDatabaseConstraint() {
        profiles.save(profile(true, localization(ContentLanguage.TR), List.of()));

        assertThatThrownBy(() -> profiles.save(profile(true, localization(ContentLanguage.EN), List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static PersonalProfile profile(boolean active, ProfileLocalization localization, List<ExternalProfileLink> links) {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                active,
                List.of(localization),
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
                List.of(new TechnicalFocusArea(
                        TechnicalFocusAreaId.newId(),
                        FocusAreaName.of("Architecture"),
                        null,
                        SortOrder.of(1))),
                List.of(new EducationSummary(
                        EducationSummaryId.newId(),
                        InstitutionName.of("University"),
                        ProgramName.of("Computer Science"),
                        null,
                        SortOrder.of(1))),
                List.of(new CurrentFocusItem(
                        CurrentFocusItemId.newId(),
                        FocusItemText.of("Research"),
                        SortOrder.of(1))));
    }

    private static ExternalProfileLink link(int sortOrder) {
        return new ExternalProfileLink(
                ExternalProfileLinkId.newId(),
                LinkLabel.of("GitHub"),
                ExternalUrl.of("https://example.test/" + sortOrder),
                SortOrder.of(sortOrder));
    }
}

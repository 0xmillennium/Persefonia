package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public record ProfileLocalization(
        ProfileLocalizationId id,
        ContentLanguage language,
        ShortBio shortBio,
        LongBio longBio,
        LocationText locationText,
        List<TechnicalFocusArea> technicalFocusAreas,
        List<EducationSummary> educationSummaries,
        List<CurrentFocusItem> currentFocusItems) {
    public ProfileLocalization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(shortBio, "shortBio");
        Objects.requireNonNull(longBio, "longBio");
        technicalFocusAreas = List.copyOf(Objects.requireNonNull(technicalFocusAreas, "technicalFocusAreas"));
        educationSummaries = List.copyOf(Objects.requireNonNull(educationSummaries, "educationSummaries"));
        currentFocusItems = List.copyOf(Objects.requireNonNull(currentFocusItems, "currentFocusItems"));
        rejectDuplicateSortOrders(technicalFocusAreas, area -> area.sortOrder(), "technical focus area");
        rejectDuplicateSortOrders(educationSummaries, summary -> summary.sortOrder(), "education summary");
        rejectDuplicateSortOrders(currentFocusItems, item -> item.sortOrder(), "current focus item");
    }

    private static <T> void rejectDuplicateSortOrders(
            List<T> values,
            Function<T, SortOrder> sortOrder,
            String label) {
        Set<SortOrder> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(sortOrder.apply(value))) {
                throw new PortfolioValidationException("duplicate " + label + " sort order");
            }
        }
    }
}

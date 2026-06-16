package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record EducationSummaryId(UUID value) {
    public EducationSummaryId {
        Objects.requireNonNull(value, "value");
    }

    public static EducationSummaryId from(UUID value) {
        return new EducationSummaryId(value);
    }

    public static EducationSummaryId newId() {
        return new EducationSummaryId(UUID.randomUUID());
    }
}

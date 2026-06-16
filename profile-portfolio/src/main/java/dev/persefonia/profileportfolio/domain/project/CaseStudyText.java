package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;

public record CaseStudyText(String value) {
    public CaseStudyText {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("case study text must not be blank");
        }
    }

    public static CaseStudyText of(String value) {
        return new CaseStudyText(value);
    }
}

package dev.persefonia.contentpublishing.domain.revision;

import dev.persefonia.contentpublishing.domain.content.ContentValidationException;

public record RevisionNumber(int value) {
    public RevisionNumber {
        if (value < 1) {
            throw new ContentValidationException("revision number must be positive");
        }
    }

    public static RevisionNumber of(int value) {
        return new RevisionNumber(value);
    }
}

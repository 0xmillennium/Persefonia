package dev.persefonia.taxonomy.application.query;

import java.util.Objects;

public sealed interface PublicTagLookupResult
        permits PublicTagLookupResult.Found, PublicTagLookupResult.NotFound {
    record Found(PublicTagView tag) implements PublicTagLookupResult {
        public Found {
            Objects.requireNonNull(tag, "tag");
        }
    }

    record NotFound() implements PublicTagLookupResult {
    }
}

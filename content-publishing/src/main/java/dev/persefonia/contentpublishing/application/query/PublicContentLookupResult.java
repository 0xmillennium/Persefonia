package dev.persefonia.contentpublishing.application.query;

import java.util.Objects;

public sealed interface PublicContentLookupResult
        permits PublicContentLookupResult.Found, PublicContentLookupResult.NotFound {

    record Found(PublicContentPageResult page) implements PublicContentLookupResult {
        public Found {
            Objects.requireNonNull(page, "page");
        }
    }

    record NotFound() implements PublicContentLookupResult {
    }
}

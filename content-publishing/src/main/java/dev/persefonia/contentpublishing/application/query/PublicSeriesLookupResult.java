package dev.persefonia.contentpublishing.application.query;

import java.util.Objects;

public sealed interface PublicSeriesLookupResult
        permits PublicSeriesLookupResult.Found, PublicSeriesLookupResult.NotFound {
    record Found(PublicSeriesPageResult page) implements PublicSeriesLookupResult {
        public Found {
            Objects.requireNonNull(page, "page");
        }
    }

    record NotFound() implements PublicSeriesLookupResult {
    }
}

# ADR 0011: Reserve Tag and Series Public Route Projections

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-15 |
| Scope | architecture / persistence |
| Supersedes | none |
| Superseded by | none |

## Context

Discovery owns the current public route projection and public route resolution.

The system needs public route shapes for tag pages and series pages before those public surfaces are implemented. The route model must preserve Discovery ownership, keep source contexts away from Discovery persistence, and avoid accidentally making tag or series pages eligible for search, feed, sitemap, or robots publication before those concerns are explicitly introduced.

## Decision

Public tag pages use the route shape:

```text
/{language}/tags/{tagSlug}
```

The accepted Discovery projection shape for tag pages is:

```text
source_context = TAXONOMY
source_type = TAG
resource_type = TAG
route_purpose = TAG_PAGE
language = TR or EN
```

Public series pages use the route shape:

```text
/{language}/series/{seriesSlug}
```

The accepted Discovery projection shape for series pages is:

```text
source_context = CONTENT_PUBLISHING
source_type = SERIES
resource_type = SERIES
route_purpose = SERIES_PAGE
language = TR or EN
```

The accepted contract additions are:

```text
SourceContext.TAXONOMY
SourceType.TAG
SourceType.SERIES
DiscoverableResourceType.TAG
DiscoverableResourceType.SERIES
RoutePurpose.TAG_PAGE
RoutePurpose.SERIES_PAGE
```

Series pages must use `RoutePurpose.SERIES_PAGE`. They must not reuse a generic `LISTING` purpose.

Tag and series page projections are initially not eligible for search, feed, or sitemap publication:

```text
indexing_policy = NO_INDEX
search_eligibility = NOT_ELIGIBLE
sitemap_eligibility = NOT_ELIGIBLE
feed_eligibility = NOT_ELIGIBLE
```

Search, feed, sitemap, and robots activation for these resources requires a later explicit decision or implementation change.

Discovery remains current-only.

No active flag is added to discoverable_resources.

No Discovery history table is introduced.

Source contexts must update or remove these projections only through Discovery application ports.

Source contexts must not construct DiscoverableResource directly.

Source contexts must not call Discovery repositories.

UNLISTED content remains direct URL only and must not appear in tag or series pages.

## Consequences

Tag and series public routes have stable shapes before their implementation.

Discovery remains the single owner of public route projection.

The explicit `TAG_PAGE` and `SERIES_PAGE` route purposes avoid overloading a generic listing concept.

Initial search, feed, and sitemap ineligibility prevents accidental public surface expansion.

Future work must add executable migrations and focused tests when these projection values become persisted.

Future indexing, feed, sitemap, or robots behavior must be introduced deliberately instead of being implied by route existence.

## Alternatives considered

- Reuse a generic `LISTING` route purpose for series pages.
- Let tag or series routes bypass Discovery.
- Add an `active` flag or history table to Discovery.
- Make tag or series pages immediately eligible for search, feed, or sitemap publication.

These alternatives were rejected because they blur route intent, weaken Discovery ownership, expand the persistence model beyond current-only projection, or widen public exposure too early.

## Review triggers

- Tag or series route shapes need to change.
- Tag or series projection ownership changes.
- Search, feed, sitemap, or robots behavior is introduced for tag or series pages.
- Discovery changes from current-only projection to historical route storage.
- Public route resolution stops going through Discovery.

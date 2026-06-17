# ADR 0011: Reserve Tag and Series Public Route Projections

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-15 |
| Scope | architecture / public routing |
| Supersedes | none |
| Superseded by | none |

## Context

The system needs stable public route shapes for tag pages and series pages before those public surfaces are fully enabled.

Reserving these projection shapes keeps links, migrations, and route resolution aligned while avoiding accidental search, sitemap, feed, or robots exposure.

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

Series pages must use `RoutePurpose.SERIES_PAGE`. They must not reuse a generic `LISTING` purpose.

Tag and series page projections are initially not eligible for search, feed, or sitemap publication:

```text
indexing_policy = NO_INDEX
search_eligibility = NOT_ELIGIBLE
sitemap_eligibility = NOT_ELIGIBLE
feed_eligibility = NOT_ELIGIBLE
```

Search, sitemap, feed, and robots activation for these resources requires a later explicit decision or implementation change.

## Consequences

Tag and series public routes have stable shapes before their implementation.

The explicit `TAG_PAGE` and `SERIES_PAGE` route purposes avoid overloading a generic listing concept.

Initial search, sitemap, and feed ineligibility prevents accidental public surface expansion.

Future indexing, feed, sitemap, or robots behavior must be introduced deliberately instead of being implied by route existence.

## Alternatives considered

- Reuse a generic `LISTING` route purpose for series pages.
- Make tag or series pages immediately eligible for search, feed, or sitemap publication.

These alternatives were rejected because they blur route intent or widen public exposure too early.

## Review triggers

- Tag or series route shapes need to change.
- Tag or series projection ownership changes.
- Search, feed, sitemap, or robots behavior is introduced for tag or series pages.
- Public route resolution stops going through Discovery.

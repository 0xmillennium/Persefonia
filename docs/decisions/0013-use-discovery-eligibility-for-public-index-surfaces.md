# ADR 0013: Use Discovery Eligibility for Public Index Surfaces

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-24 |
| Scope | architecture |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia has public content, project, tag, series, CV, and media image routes. Search, sitemap, and feed surfaces make eligible public resources discoverable.

The Discovery current projection already owns indexing, search, sitemap, and feed eligibility fields. Public index surfaces must not bypass that projection by querying source-context repositories directly.

## Decision

Dynamic public search, sitemap, and feed entries use the Discovery current projection as their source of truth, stored in `discovery.discoverable_resources`.

Search includes only resources with `indexing_policy = INDEX` and `search_eligibility = ELIGIBLE`. Sitemap includes dynamic resources only when `indexing_policy = INDEX` and `sitemap_eligibility = ELIGIBLE`. Feed includes entries only when `indexing_policy = INDEX` and `feed_eligibility = ELIGIBLE`.

`UNLISTED` resources remain direct URL only and are excluded from search, sitemap, feed, broad navigation, and hreflang alternates. `PRIVATE`, draft, unpublished, and archived resources are excluded from public index surfaces.

Listed public project detail pages may become search and sitemap eligible. Projects remain feed ineligible. Tag and series pages remain `NO_INDEX` and are not search, sitemap, or feed eligible unless a later ADR changes that policy.

Generic Media PDF/original/download routes remain forbidden. Media image variants and CV downloads are excluded from search, sitemap, and feed.

## Consequences

Public index behavior has one dynamic source of truth, and source contexts project public state through Discovery ports without exposing their repositories to public controllers.

Direct URL only content remains shareable without becoming discoverable through index surfaces. Future maintainers must never infer index eligibility from route existence alone.

## Alternatives considered

- Query source-context repositories directly from search, sitemap, or feed controllers. Rejected because it violates bounded-context boundaries.
- Treat every public route—including tag and series pages—as indexable by default. Rejected because it makes public discovery implicit.

## Review triggers

- Discovery no longer owns the current public projection, or public index surfaces require another dynamic source.
- `UNLISTED` visibility behavior changes.
- Tag, series, project, CV, or media binary index eligibility changes.

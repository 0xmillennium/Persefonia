# ADR 0013: Use Discovery Eligibility for Public Index Surfaces

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-24 |
| Scope | architecture / public indexing |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia has public content, project, tag, series, CV, and media image routes. Search, sitemap, and feed surfaces will make some public resources easier to discover.

The system already has a Discovery current projection with explicit indexing, search, sitemap, and feed eligibility fields. Public indexing must not bypass that projection by querying source-context repositories directly from the public web layer.

## Decision

Dynamic public search, sitemap, and feed entries use the Discovery current projection as their source of truth. The current projection is stored in `discovery.discoverable_resources`.

Search includes only resources with `indexing_policy = INDEX` and `search_eligibility = ELIGIBLE`.

Sitemap includes dynamic resources only when `indexing_policy = INDEX` and `sitemap_eligibility = ELIGIBLE`.

Feed includes entries only when `indexing_policy = INDEX` and `feed_eligibility = ELIGIBLE`.

`UNLISTED` resources remain direct URL only. They are excluded from search, sitemap, feed, broad navigation, and hreflang alternates.

`PRIVATE`, draft, unpublished, and archived resources are excluded from public index surfaces.

Listed public project detail pages may become search and sitemap eligible. Projects remain feed ineligible.

Tag and series pages remain `NO_INDEX` and are not search, sitemap, or feed eligible unless a later ADR changes that policy.

Generic Media PDF/original/download routes remain forbidden. Media image variants and CV downloads are excluded from search, sitemap, and feed.

## Consequences

Public index behavior has one dynamic source of truth.

Source contexts can keep projecting public state through Discovery ports without exposing their repositories to public controllers.

Direct URL only content remains shareable without becoming discoverable through index surfaces.

Project indexing requires implementation work in the project projection and persistence constraints before it can take effect.

Future maintainers must not infer index eligibility from route existence alone.

## Alternatives considered

- Query Content Publishing, Profile and Portfolio, Taxonomy, or Media repositories directly from search, sitemap, or feed controllers.
- Treat every public route as indexable by default.
- Promote tag and series pages to search or sitemap eligibility because the pages exist.

These alternatives were rejected because they weaken bounded-context boundaries and make public discovery too implicit.

## Review triggers

- Discovery no longer owns the current public projection.
- Public search, sitemap, or feed needs a dynamic source other than Discovery.
- `UNLISTED` visibility behavior changes.
- Tag, series, project, CV, or media binary index eligibility changes.

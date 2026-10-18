# ADR 0012: Constrain Public Navigation and Hreflang to Listed Public Content

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-16 |
| Scope | architecture / security / testing |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia has public content detail pages, public tag pages, public series pages, and public translation metadata. These surfaces expose relationships between content items and can make otherwise hard-to-discover content easier to find.

The system distinguishes listed public content from direct URL only content. That distinction must remain clear so relationship pages, visible translation links, and hreflang metadata do not broaden exposure accidentally.

## Decision

Public navigation surfaces (public tag pages, public series pages, visible public translation links, and public hreflang alternates) expose only content that is both `PUBLISHED` and `PUBLIC`.

`UNLISTED` content remains direct URL only. It must be accessible by its valid current detail URL, but excluded from public tag pages, series pages, visible translation links, and hreflang alternates. `PRIVATE`, `DRAFT`, `UNPUBLISHED`, and `ARCHIVED` content are excluded from all public navigation surfaces.

Public tag pages and public series pages are public read surfaces, but they remain `NO_INDEX` and ineligible for search, feed, or sitemap publication.

Public translation links and hreflang alternates render only on public content detail pages, never for `UNLISTED` content. `x-default` hreflang is intentionally absent until Persefonia adopts a durable default-language policy.

## Consequences

Listed public content has predictable relationship navigation, while direct URL only content remains shareable without becoming discoverable through relationship surfaces.

Public tag and series pages can exist without implying indexing, search, feed, or sitemap eligibility.

Future maintainers must keep relationship read models aligned with `PUBLISHED` plus `PUBLIC` exposure rules and revisit hreflang explicitly before adding `x-default`.

## Alternatives considered

- Include `UNLISTED` content in tag pages, series pages, or hreflang while keeping it out of indexes. Rejected because it weakens the direct URL only contract.
- Render self-only hreflang on content without eligible alternates or add `x-default` prematurely. Rejected because it creates noisy or misleading metadata.
- Treat tag and series pages as indexable because they are public routes. Rejected because it expands public discovery without durable policy.

## Review triggers

- The meaning of `UNLISTED` or public content visibility states changes.
- A default-language policy is adopted.
- Search, feed, sitemap, or robots behavior is introduced for tag, series, or translation surfaces.
- Public relationship surfaces expand beyond tag pages, series pages, and translations.

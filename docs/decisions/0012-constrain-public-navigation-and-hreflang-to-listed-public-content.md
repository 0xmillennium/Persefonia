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

Public navigation surfaces expose only content that is both `PUBLISHED` and `PUBLIC`.

Public navigation surfaces include:

- public tag pages
- public series pages
- visible public translation links
- public hreflang alternates

`UNLISTED` content remains direct URL only. It must be accessible by its valid current detail URL, but it must be excluded from public tag pages, public series pages, visible translation links, and hreflang alternates.

`PRIVATE`, `DRAFT`, `UNPUBLISHED`, and `ARCHIVED` content are excluded from public navigation surfaces.

Public tag pages and public series pages are public read surfaces, but they remain `NO_INDEX`. They are not eligible for search, feed, or sitemap publication.

Public translation links and hreflang alternates render only on public content detail pages. They must not render for `UNLISTED` content.

`x-default` hreflang is intentionally absent until Persefonia has a durable default-language policy.

Tag, series, and translation surfaces do not introduce search, feed, sitemap, or robots behavior beyond the existing noindex metadata required for public tag and series pages.

## Consequences

Listed public content has predictable relationship navigation.

Direct URL only content remains shareable by URL without becoming discoverable through relationship surfaces.

Public tag and series pages can exist without implying indexing, search, feed, or sitemap eligibility.

Future maintainers must keep relationship read models aligned with `PUBLISHED` plus `PUBLIC` exposure policy and must keep hreflang generation out of direct URL only content.

Any future default-language policy must explicitly revisit hreflang behavior before adding `x-default`.

## Alternatives considered

- Include `UNLISTED` content in tag pages, series pages, or hreflang while keeping it out of indexes.
- Render self-only hreflang on content without eligible alternates.
- Add `x-default` before defining a default-language policy.
- Treat tag and series pages as indexable because they are public routes.

These alternatives were rejected because they weaken the direct URL only contract, create noisy or misleading metadata, or expand public discovery before the system has durable policy for those behaviors.

## Review triggers

- The meaning of `UNLISTED` changes.
- A default-language policy is adopted.
- Search, feed, sitemap, or robots behavior is introduced for tag, series, or translation surfaces.
- Public relationship surfaces are expanded beyond tag pages, series pages, and translations.
- Public content visibility states change.

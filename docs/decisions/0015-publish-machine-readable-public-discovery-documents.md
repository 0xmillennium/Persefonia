# ADR 0015: Publish Machine Readable Public Discovery Documents

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-24 |
| Scope | architecture |
| Supersedes | none |
| Superseded by | none |

## Context

Sitemap, robots, and feed documents are public machine-readable surfaces that help clients discover intended public resources without acting as authorization or exposing direct URL only resources.

Persefonia also serves static public routes—the home page, project listings, and the active CV page—that are outside dynamic Discovery projections.

## Decision

Sitemap XML uses absolute URLs. Dynamic sitemap entries come from Discovery sitemap eligibility. Static sitemap entries come from a small explicit allowlist: the home page, project listing pages, and the CV page only when an active CV exists.

Sitemap excludes search pages, search results, feed URLs, sitemap URLs, robots URLs, CV download URLs, media binary URLs, admin URLs, OAuth URLs, preview URLs, and actuator URLs.

`robots.txt` is advisory and not security. It references the absolute sitemap URL and disallows admin, actuator, OAuth/login/logout, preview, and search paths.

The first feed format is Atom 1.0. Feed entries come from Discovery feed eligibility and are summary-only. Content articles, notes, and research resources may be feed eligible; content pages, projects, tags, series, CV, and media binaries are feed ineligible.

Indexable public pages require canonical URLs and safe title/description metadata. The system must not invent fake OpenGraph images, default project cover placeholders, or generic Media URLs for OpenGraph metadata.

Sitemap, robots, and feed responses use explicit public cache. Search query responses, admin, OAuth, preview, actuator, and state-changing responses are never public-cacheable.

## Consequences

Public crawlers receive stable discovery documents without treating every public route as sitemap-worthy, while security remains enforced by route absence and access control.

Atom-only feed support keeps feed behavior narrow until another format is needed, and future maintainers must keep static route inclusion explicit.

## Alternatives considered

- Generate sitemap entries from every public route. Rejected because not all public routes are indexable.
- Treat `robots.txt` as a security boundary. Rejected because crawler directives do not enforce access control.
- Publish RSS and Atom simultaneously or use placeholder OpenGraph images. Rejected because they widen scope or publish misleading metadata.

## Review triggers

- Static public routes or feed format requirements change.
- Search pages become indexable or default OpenGraph media policy changes.
- Sitemap, robots, or feed cache policy changes.

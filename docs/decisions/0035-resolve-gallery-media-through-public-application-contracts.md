# ADR 0035: Resolve Gallery Media Through Public Application Contracts

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-13 |
| Scope | architecture / security / persistence |
| Supersedes | none |
| Superseded by | none |

## Context

Public homepage publications can display an author-selected cover image, but a stored asset UUID alone does not authorize public delivery. Content visibility and media asset eligibility are owned by separate bounded contexts, and an image asset can be revoked independently of its publication.

## Decision

Project authoring attaches or clears `cover_asset_id` through its authorized command gateway, while writing authoring reuses its existing Open Graph image relationship for homepage gallery presentation.

Discovery supplies listed publications, Content Publishing and Profile & Portfolio return cover references only for eligible public items, and source contexts never query Media Library repositories directly.

The public web composition resolves cover references through Media Library's public image query port. Only `PUBLIC`, `PROCESSED` images with an available named variant are passed to JTE as presentation URLs and dimensions; unavailable, private, or missing images degrade to the semantic fallback. PostgreSQL remains the sole relationship authority, with no duplicated frontend publication dataset.

## Consequences

Existing media persistence and delivery contracts support homepage covers without new schema tables or cross-context repository coupling.

Homepage rendering re-evaluates media eligibility on read so revoked or failed assets fall back cleanly without relying on cache invalidation for exposure correctness ([ADR 0019](0019-keep-cache-invalidation-outside-public-exposure-correctness.md)).

## Alternatives considered

- Hardcode gallery items and image URLs in frontend assets. Rejected because publication state, localization, and visibility belong to PostgreSQL and bounded-context projections.
- Add a separate writing cover schema column immediately. Rejected because reusing the Open Graph image satisfies current needs without a migration.
- Treat a stored cover UUID as unconditionally public. Rejected because media visibility and variant readiness can change independently.

## Review triggers

- Gallery covers and Open Graph images require independent assets or crops.
- Homepage media resolution exceeds bounded query limits.
- Public media eligibility or caching policy changes.

# ADR 0035: Resolve Gallery Media Through Public Application Contracts

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-13 |
| Scope | architecture / security / persistence / testing |
| Supersedes | none |
| Superseded by | none |

## Context

Public homepage publications can carry an author-selected image. A stored asset UUID alone does not authorize public delivery. Content eligibility and image eligibility have different owners, and an asset can be revoked independently of its publication.

## Decision

Project authoring attaches or removes the existing `cover_asset_id` through the authorized application command gateway. Writing authoring reuses its existing Open Graph image relationship for gallery presentation. A writing cover therefore also serves as its social image; independent cropping or a second relationship requires a separate product decision.

Discovery supplies the listed publication and its source identity. Content Publishing validates current published/public status, current route and renderability before returning cover metadata. Profile & Portfolio supplies project cover references with eligible public summaries. Source contexts do not query Media Library persistence or construct Discovery-owned state.

The public web composition resolves references using Media Library's public image application query. Only public, processed images with an existing named variant are delivered to JTE as presentation views. Unavailable, private, failed, removed or missing images yield the semantic fallback. Public templates receive variant URLs and dimensions, never storage paths, asset repositories or source aggregates.

Admin forms may expose an attachment UUID. This does not grant access to the image or relax upload, processing, authorization, Audit, cache invalidation, or public media delivery checks. PostgreSQL remains the relationship authority; no duplicated frontend publication dataset is maintained.

## Consequences

Existing persistence and media delivery remain reusable without a new schema or storage service. Administrators can attach a Media Library UUID, and publications without a usable cover remain navigable.

Cover resolution adds bounded application reads to the homepage. The public response must re-evaluate image eligibility; correctness must not depend on a stale cached URL or invalidation succeeding. A previously delivered URL remains subject to the media endpoint's eligibility checks.

Tests distinguish authorized admin attachment from public media exposure. Public storage internals remain forbidden, while treating every admin cover UUID as a prohibited internal is no longer an appropriate boundary test.

## Alternatives considered

- Maintain separate gallery content and image URLs in frontend code. Rejected because publication, localization and exposure would diverge from the source application.
- Add dedicated writing cover persistence now. Deferred because the existing author-selected Open Graph image supplies the required image without a second relationship or schema migration.
- Treat a stored asset UUID as permission to publish its bytes. Rejected because asset visibility, processing and variant availability can change independently.

## Review triggers

- Gallery and Open Graph images need independent authoring or crops.
- Homepage media enrichment exceeds the bounded publication limits.
- Public media eligibility or publication caching changes.

# ADR 0036: Version Homepage Film Delivery Assets With the Application

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-13 |
| Scope | architecture / operations / testing |
| Supersedes | none |
| Superseded by | none |

## Context

The interactive homepage consumes authored frame sequences. Production builds must be reproducible from application source without an ignored experiment cache or original media library. Public delivery must remain self-hosted and consistent with the immutable OCI release model.

## Decision

Version the qualified homepage WebP delivery assets and their manifest/qualification receipt under `frontend/public/home-films/`. Preserve the approved source frames outside public delivery. Publish derivatives explicitly through `npm run home:films:publish`, consuming the existing film preparation's full-integrity receipt and copying bytes unchanged. Publication does not encode or regenerate films.

Ordinary Vite builds consume only these repository delivery assets. Build preflight validates the preparation contract, identity, manifests, qualification receipt, frame inventory, dimensions declared in the manifest and file sizes. Full hashes and pixel decoding remain part of explicit film preparation; repeat them when publication changes, not for every frontend edit.

Vite packages both delivery tiers into the BootJar intermediate, and the existing OCI image contains them. Versioned public URLs include the delivery identity. Runtime does not read `.local/`, experiment caches or source-frame directories. ADR 0022 continues to govern deployment and same-byte promotion; local BootJar review is not a production deployment method.

## Consequences

Clean builds need no media service credentials or source films. Existing qualified delivery files add approximately 60 MB to repository source and release artifacts. Updates must include the complete matching delivery set and receipt; partial or unqualified publication fails the build. Original source preparation still requires the approved source library and FFmpeg.

Publications and CMS covers remain Media Library responsibilities under ADR 0035. Authored homepage films are application presentation assets, not a parallel CMS or domain storage system.

## Alternatives considered

- Consume ignored prepared caches during production builds. Rejected because a clean CI checkout lacks those files and qualification provenance.
- Provision a separately hosted media artifact. Deferred because it adds an external build dependency, credentials and distribution ownership for a bounded, stable delivery set.
- Serve media from an external runtime CDN. Rejected because it conflicts with current self-hosting and release reproducibility requirements.

## Review triggers

- Delivery asset growth makes repository versioning impractical.
- Film content changes frequently or needs independent release ownership.
- An accepted artifact-distribution service can supply immutable, qualified build inputs.

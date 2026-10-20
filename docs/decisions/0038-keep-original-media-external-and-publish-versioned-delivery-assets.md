# ADR 0038: Keep Original Media External and Publish Versioned Delivery Assets

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-20 |
| Scope | architecture / operations / testing |
| Supersedes | ADR 0036 |
| Superseded by | none |

## Context

[ADR 0036](0036-version-homepage-film-delivery-assets-with-the-application.md) versions qualified homepage delivery but locates offline sources in ignored repository storage. Original media is maintained separately, and extracted frame libraries need not persist in the checkout. Clean builds and runtime delivery must remain independent of original-media availability and encoding tools.

## Decision

Maintain original videos and images outside the repository. Intentional offline publication accepts explicit original paths, validates source contracts and records source and processing identities. Extracted/prepared frames are disposable processing intermediates in an owned temporary workspace, removed on completion or failure; they are not permanent source storage or browser delivery assets. Encoding changes require deliberate republication and qualification, without claiming equivalence to earlier bytes.

Retain qualified H.264 scroll films, opening/final WebP posters and waterfall video/poster derivatives under `frontend/public/`, versioned with immutable manifests and SHA-256 receipts. Ordinary Gradle/Vite builds validate manifest contracts, dimensions, sizes and hashes without originals, frame caches or FFmpeg. Delivery remains self-hosted inside the immutable OCI artifact under ADR 0022.

Paused video drives scroll scrubbing and active WebGL shader textures. Looping media pauses when hidden, covered or under reduced motion. Unavailable decoding and reduced motion degrade to static posters without blocking navigation or semantic server-rendered content.

The build emits an immutable URL inventory for hashed bundles and versioned film/poster assets, eligible for one-year immutable caching and HTTP byte-range serving. Exclude HTML, manifests and revocable CMS media, preserving ADR 0035 eligibility boundaries.

## Consequences

Clean checkouts remain buildable without source archives or an encoding toolchain. Regeneration requires separately maintained originals and qualified offline processing; committed delivery is not an original-media backup. Intermediate cleanup bounds storage, while corrupt or mismatched delivery fails the build.

## Alternatives considered

- Permanent extracted sequences: rejected as redundant processing storage and a second runtime playback engine.
- Encode during ordinary builds: rejected because it couples builds to originals and encoders.
- Remove poster fallbacks: rejected because unavailable decoding must preserve usable content.
- External delivery hosting: rejected to preserve self-hosted CSP and single-artifact promotion.

## Review triggers

- Versioned media outgrows practical Git storage.
- Original-media custody or reproducible publication requirements change.
- Delivery needs an independent release cadence or external immutable asset store.

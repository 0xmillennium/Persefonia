# ADR 0036: Version Homepage Film Delivery Assets With the Application

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-10-18 |
| Scope | architecture / operations / testing |
| Supersedes | none |
| Superseded by | ADR 0038 |

Superseded by ADR 0038; see the [index](INDEX.md).

## Context

The homepage presents two scroll-scrubbed films and an independently looping waterfall background. Clean CI and local builds must be reproducible without ignored `.local/media/` source archives or FFmpeg encoding, while keeping delivery self-hosted inside the immutable OCI release artifact ([ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md)).

## Decision

Qualified H.264 scroll films (`frontend/public/home-videos/`), compact opening/final WebP posters (`frontend/public/home-posters/`), and looping waterfall derivatives (`frontend/public/home-loops/`) are versioned with the application alongside their immutable manifests and SHA-256 receipts.

Ordinary Gradle and Vite builds verify manifest contracts, file sizes, WebP dimensions, and SHA-256 hashes without invoking FFmpeg or reading `.local/media/`. Offline publication scripts (`media:frames:prepare`, `media:videos:publish`, `media:posters:publish`, `media:loop:publish`) encode immutable `.local/media/` sources only when assets are intentionally republished.

At runtime, paused video drives scroll scrubbing and active WebGL shader textures, while looping media plays natively and pauses when hidden, covered, or under `prefers-reduced-motion`. If video decoding fails or reduced motion is active, presentation degrades to static opening/final WebP posters without blocking navigation or SSR content.

The build emits an immutable URL inventory (hashed Vite bundles and versioned film/poster assets) eligible for one-year immutable public caching and HTTP byte-range serving, excluding HTML, manifests, and revocable CMS media ([ADR 0035](0035-resolve-gallery-media-through-public-application-contracts.md)).

## Consequences

Clean checkouts and CI builds require only committed, hash-verified delivery assets and no external media service or encoding toolchain.

Corrupt or mismatched delivery files fail the build immediately, and runtime video failures degrade to static posters rather than retaining a redundant 768-image WebP sequence player.

## Alternatives considered

- Retain the 768-frame WebP sequence alongside native video. Rejected because it duplicates ~61 MB of delivery assets and a second playback engine.
- Encode source media during ordinary builds. Rejected because clean builds must not depend on ignored `.local/media/` archives or FFmpeg.
- Remove static poster fallbacks. Rejected because unavailable video decoding must leave a usable presentation without blocking navigation.
- Host film delivery externally now. Rejected to preserve self-hosted CSP and single-artifact OCI promotion ([ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md)).

## Review triggers

- Versioned media size outgrows Git repository practicality.
- Homepage films require independent release cadences or an external immutable artifact store.

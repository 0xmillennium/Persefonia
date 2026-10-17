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

### Seekable video delivery

The homepage also versions seekable H.264 delivery derivatives under `frontend/public/home-videos/`. Explicit `npm run home:videos:publish` consumes the qualified published WebP frames, checks their hashes, and encodes the same 192 poses at both existing dimensions. The preparation contract fixes the frame rate, quality, pixel format, three-frame keyframe interval and encoder version. Publication verifies frame count, dimensions, keyframe positions and full decoding, then records output hashes and source provenance. Ordinary builds check the matching manifests, qualification receipt, inventory and sizes without encoding or requiring FFmpeg.

Runtime uses paused native video for plain scroll scrubbing and the existing GPU surface for active navigator/hold effects. The qualified WebP sequences remain the fallback when video delivery or decoding fails. Video URLs include their own immutable delivery identity; runtime media remain same-origin without a blob/MediaSource dependency or CSP relaxation. This adds approximately 34 MB to the versioned delivery set. Native video reduces repeated image decoding and GPU texture submission during plain scrubbing; physical-device and browser-specific performance still require runtime qualification.

The explicitly approved waterfall background adds continuous playback alongside the two scroll-driven films. Its publication is independent: `npm run home:loop:publish` consumes the approved source at `.local/media/home/loop-video/loop video.mp4`, preserves its 24 fps and eight-second duration, and publishes silent 1280px/1920px H.264 faststart files and matching posters under `frontend/public/home-loops/`. The source stays outside public delivery. A separate immutable contract records the source hash, encoder, quality, dimensions, playback GOP, output hashes and full-decode receipt; ordinary builds validate the published inventory without accessing the source or encoding media. It does not weaken the scroll films' exact-pose or short-GOP qualification.

Background selection cycles independently of playback mode. The waterfall uses native looping playback and refreshes shader textures from decoded-frame callbacks only during navigator/hold effects. Inactive, fully covered, hidden-page, menu-covered and reduced-motion playback pauses. The visible loop plays automatically without a separate playback button. A poster preserves usable presentation when playback is unavailable; the existing background gesture or film selection can retry playback. Background crop and opening geometry remain fixed for each viewport and source during scrolling, including both sides of a switch. The same server-rendered content and self-hosted CSP boundary continue to apply.

## Alternatives considered

- Consume ignored prepared caches during production builds. Rejected because a clean CI checkout lacks those files and qualification provenance.
- Provision a separately hosted media artifact. Deferred because it adds an external build dependency, credentials and distribution ownership for a bounded, stable delivery set.
- Serve media from an external runtime CDN. Rejected because it conflicts with current self-hosting and release reproducibility requirements.

## Review triggers

- Delivery asset growth makes repository versioning impractical.
- Film content changes frequently or needs independent release ownership.
- An accepted artifact-distribution service can supply immutable, qualified build inputs.

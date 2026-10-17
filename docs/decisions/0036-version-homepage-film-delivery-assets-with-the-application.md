# ADR 0036: Version Homepage Film Delivery Assets With the Application

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-13; updated 2026-10-18 |
| Scope | architecture / operations / testing |
| Supersedes | none |
| Superseded by | none |

## Context

The homepage presents two scroll-driven authored films and an independently looping waterfall. Builds must be reproducible without an ignored preparation cache, source media library or media credentials. Delivery remains self-hosted and consistent with immutable OCI releases. The initial image-sequence player was replaced by native video; retaining its complete publication as a second runtime player duplicated delivery and recovery complexity.

## Decision

Version qualified H.264 scroll films under `frontend/public/home-videos/`, compact opening/final WebP posters under `frontend/public/home-posters/`, and waterfall video/poster derivatives under `frontend/public/home-loops/`. Each publication has an immutable identity, byte/hash inventory and qualification receipt. Vite includes the delivery assets in the BootJar intermediate and OCI image. ADR 0022 continues to govern deployment and same-byte promotion; a local BootJar preview is not a deployment method.

Ordinary builds validate publication contracts, matching manifests and receipts, complete inventories, file sizes and actual SHA-256 delivery hashes. Poster dimensions are read from their WebP container. Same-size corruption fails validation. Builds neither encode nor require FFmpeg, ignored source frames or a historical public frame publication. Full codec/pixel qualification belongs to explicit preparation/publication and repeats when delivery changes.

### Explicit publication and source provenance

Preserve immutable source footage/frames outside public delivery. `npm run media:frames:prepare` validates original frame inventory and integrity and prepares the working derivatives under `.local/media/home/prepared-scroll/`. The source inventory, transformation contract/operator hash, source hash and preparation receipt record provenance. Keep a recoverable source-media archive and its matching receipts outside the application delivery set; `.local/` is not a production dependency or a durable architectural authority.

`npm run media:videos:publish` directly consumes this qualified preparation. It encodes 192 poses at 30 fps in 960px/1600px H.264 faststart derivatives, with no B frames and a three-frame keyframe interval. Publication verifies dimensions, frame count, duration, keyframe positions and full decoding; source identity and manifest hash remain in the video contract/receipt. Historical source lineage does not require distributing every source pose to the browser. Existing video bytes and URLs remain unchanged when only the fallback publication changes.

`npm run media:posters:publish` copies only opening/final qualified WebP poses at the same two sizes. Its own identity and receipt bind the exact selection to the preparation identity and manifest hash used by the videos. Eight images replace the former 768-image runtime publication. No sequence cache or sequence playback remains in production. The archived source preparation and its validator remain offline encoding tools.

### Runtime presentation and failure behavior

Native paused video supplies plain scroll scrubbing and GPU textures for active navigator/hold effects. Desired video pose and usable presentation are separate states. A bounded video failure selects a static opening poster while the initial window sheet is visible, or a static final poster in the later film area. A retained usable presentation survives resolution/pose replacement preparation. A poster is explicitly static and is never reported as a decoded requested video frame. Reduced motion can prepare a static poster without starting a scroll-video decoder.

Film switching may reveal a usable incoming poster when native decoding fails. Missing video/poster resources cannot hold the loader or source-switch state indefinitely; semantic content and navigation remain available. The existing explicit gesture/keyboard selection retries failed media at the current scroll target. Readiness means a usable presentation, not necessarily successful video decoding. Accessible notices describe meaningful failure/presentation changes, not every film pose. No frame-by-frame canvas copy is introduced as failure insurance.

The waterfall publication remains independent: `npm run media:loop:publish` consumes the approved source at `.local/media/home/loop-video/loop video.mp4`, preserves 24 fps/eight seconds, and publishes silent 1280px/1920px H.264 faststart files and matching posters. Its contract records source hash, encoder, quality, dimensions, playback GOP, output hashes and full-decode receipt. This does not weaken scroll-film exact-pose/short-GOP qualification.

Background selection is independent of playback mode. The waterfall loops natively and refreshes shader textures from decoded-frame callbacks only during effects. Inactive, fully covered, hidden-page, menu-covered and reduced-motion playback pauses. It needs no separate playback button. Background crop and opening geometry remain fixed for each viewport/source during scrolling and on both sides of a switch. SSR content and CSP boundaries remain unchanged; no blob/MediaSource dependency is introduced.

### Release asset delivery

The build emits an exact qualified immutable URL inventory for one-year public immutable caching. It includes hashed Vite outputs and versioned presentation media, excluding fixed manifests, HTML, theme CSS and revocable CMS media. Native video byte ranges remain supported. Production text compression covers scripts/styles/SVG, not encoded video. Regression checks exercise actual HTTP GET/range behavior; external ingress must preserve these policies and requires deployment verification.

## Consequences

Clean application builds need only versioned delivery assets. Videos add approximately 34 MB, loop delivery 21 MB and scroll-film posters 0.56 MB. Removing the complete sequence publication saves approximately 61 MB before metadata differences. Git history retains previously committed objects unless a separately authorized history migration removes them.

Partial, incompatible or corrupt publications fail the build. Preparation still needs the source archive and qualified encoding tools. Runtime failure intentionally reduces cinematic motion while retaining usable content. Physical-device playback, input and CPU/GPU performance need browser/hardware qualification.

Publication covers remain Media Library responsibilities under ADR 0035. Homepage films are application presentation assets, not another CMS or domain storage system.

## Alternatives considered

- Keep the complete WebP sequence as video recovery: rejected because it maintains another playback/decoding scheduler and duplicates approximately 61 MB of delivery. A small static presentation preserves usable content without that second engine.
- Require source preparation during ordinary builds: rejected because clean application builds must not depend on ignored source media, FFmpeg or encoding work.
- Remove all static media recovery: rejected because unavailable video must leave a deliberate usable presentation and must not block navigation or film selection.
- Move delivery to an external artifact/media service now: deferred until an accepted immutable distribution contract exists. Repository-owned qualified assets preserve self-hosting and same-byte promotion.

## Review triggers

- Delivery growth makes repository versioning impractical.
- Films require frequent independent releases.
- An accepted artifact service can supply immutable qualified build inputs.

# Experiment 02 — scroll film & liquid navigator

An isolated interaction experiment for **0xmillennium**. The accepted remediation contract is recorded in [CONTRACT.md](CONTRACT.md); measured results, limitations and review evidence are in [QUALIFICATION.md](QUALIFICATION.md). This does not integrate the experiment into the production homepage.

Native document scroll selects `round(progress × 191)` from one of two immutable 192-frame PNG sequences. There is no autoplay or playback clock. Film selection preserves normalized progress. The locked Triptych analysis informs liquid navigation and the outward reveal; its autoplay, four films and repeating hold are deliberately superseded by the task contract.

A hold grows translucent turquoise material over the **current** film. Early release erodes the stored spatial field; re-press resumes that field and retargets its center. At full coverage the correct incoming frame must be ready. An aperture then expands from the captured press center, showing the incoming film inside and tinted outgoing film outside, with a localized soft white boundary. A continuous physical press can commit once, then requires release. Earned readiness/reveal survives release; preparation failure times out through a controlled cancellation.

## Run and qualify

From `frontend/`, using the existing pinned toolchain:

```sh
npm run film:assets
npm run film:prepare
npm run film:dev             # http://127.0.0.1:4180
npm run film:typecheck
npm run film:test
npm run film:build
npm run film:browser         # all six configured desktop/touch projects
npm run film:browser:built   # built artifact, port 4181
npm run film:perf            # primary WebGL2, observed backend reported explicitly
npm run film:record          # canonical desktop + native Chromium touch, port 4182
npm run film:check           # typecheck, unit, assets, build, browser, built browser
node experiments/scroll-film-navigator/scripts/browser-preflight.mjs
node experiments/scroll-film-navigator/scripts/benchmark-prepare.mjs
node experiments/scroll-film-navigator/scripts/media-quality.mjs
FILM_BENCH_MODES=baseline-webp,directional2-webp,intra-vp9 FILM_BENCH_LABEL=benchmark-final node experiments/scroll-film-navigator/scripts/benchmark.mjs
node experiments/scroll-film-navigator/scripts/capture-transport.mjs
node experiments/scroll-film-navigator/scripts/material-study.mjs
node experiments/scroll-film-navigator/scripts/bfcache.mjs
```

FFmpeg with libwebp is required for offline preparation. Indexed VP9 and motion-interpolation studies additionally use the installed FFmpeg encoders/filters. No npm dependencies were added. Outputs are ignored under `frontend/test-results/scroll-film-navigator/` and `frontend/dist/scroll-film-navigator/`. Source PNGs remain ignored and immutable under this experiment's `assets/frames/` directory. No fallback MP4 or new creative film is used.

## Media and ownership

Preparation hashes and validates all 384 source PNGs, including signature/chunk integrity, order and 3840×2160 dimensions. It generates WebP q82 at 960×540 and 1600×900 using Lanczos scaling. A SHA-256 version identifies the source inventory, encoder/version, options and preparation contract. Each of the 768 derivatives has a byte/hash/dimension inventory and a complete FFmpeg pixel-decode check. Fresh preparation verifies unchanged sources before publishing an immutable version directory, then atomically replaces the active manifest. A process lock prevents concurrent publication. Reuse validates the complete inventory and full decodes; missing or corrupt assets fail closed.

HTML contains a real responsive first-frame poster before JavaScript. Opening-frame decode gets priority; the next film is prepared only for an eligible hold or explicit selection. The tier is chosen once per session (960 through 700 CSS pixels, otherwise 1600) to avoid resize churn. Native image selection and bitmap decode use the same versioned asset URL.

The main decoded window owns at most 20 bitmaps and two concurrent loads. Direction and observed traversal speed adjust preparation priority; neither changes scroll progress. Aborted/stale completions cannot replace a newer target, and evicted bitmaps are closed. The two resident film textures pin their cache-owned bitmaps. Stable-size updates use `texSubImage2D`; film switching exchanges texture ownership. Draw submission is reported separately from captured compositor pixels and physical screen presentation.

## Rendering and fallbacks

The primary renderer uses one WebGL2 context. GPU-owned velocity, dye, pressure, divergence and curl targets implement advection, pressure projection and vorticity. Bounded MacCormack correction preserves transported dye veins; movement deposits a distance-based dose and a narrower wake at higher speed. The shader composites film refraction, turquoise pigment, the persistent hold mask, aperture and white front. The hold mask is a bounded CPU signed-distance lattice, uploaded only when changed; it is not regenerated from an analytic circle on re-press. GPU timing covers simulation, uploads and composition where the extension is available. Explicit development-only readback studies are excluded from ordinary rendering. Pointer samples form an ordered queue of at most 32 segments; forward-collinear segments may merge, and overflow ages out old segments without extending the final tangent. At most 32 arc-length deposits are submitted per frame. Tiny coalesced movements accumulate until a segment can be emitted. Submitted line dose is conserved across long batches; GPU pigment concentration remains bounded, so identical final mass across different aging/advection histories is not promised. A long preceding stall expires old pigment while retaining fresh pending deposits. Fluid transport takes one step capped at 1/30 second; excess elapsed transport time is deliberately discarded rather than accumulated into expensive catch-up passes. Pigment fading and expiry follow actual elapsed time. Diagnostics expose discarded transport time and dropped segments, so stalled-frame fidelity remains measurable.

Integrated graphics with shared system RAM is a normal target; a discrete GPU or separate VRAM is not required. The renderer uses the browser's default power preference, bounds backing resolution/DPR and simulation grids, reuses textures, skips inactive hold refraction and stops scheduling when settled. Decoded bitmaps and graphics allocations must be considered together on shared-memory systems. Normal qualification uses the browser's default graphics path; `FILM_SOFTWARE_GPU=1` explicitly selects SwiftShader for a separate regression run. Reports identify the actual backend and memory estimates; a software-rendered VM does not establish accelerated integrated-graphics performance.

Without usable float WebGL2 targets, a clearly labeled basic Canvas2D mode preserves film/hold semantics with reduced material quality. If graphics contexts cannot initialize, a semantic image mode preserves native scroll and film selection. Without JavaScript, the poster and semantic instructions remain readable. These fallbacks do not qualify the full liquid effect.

Fine pointers retain the native cursor and add a tightly tracking core. Touch starts a hold immediately; movement beyond the gesture threshold cancels incomplete growth and leaves native pan/pinch available. Semantic buttons, the progress slider and visible focus provide keyboard access. Reduced motion removes fluid and spatial reveal while retaining film selection. Resize cancels transient hold/reveal back to the outgoing scene; visibility suspension preserves pigment briefly and ages it by real elapsed absence. History preserves selected film and native scroll. Context loss, abort and disposal explicitly release ownership.

## Evidence limits

A running RAF loop or correct state index does not prove visible continuity. The candidate harness measures target generations and records canonical pixels; the integrated capture harness decodes a development-only source-identity band from actual compositor JPEGs and saves the timestamped frame sequence. Capture cadence is measured and can undersample 48 changes/second. Accelerated integrated-graphics performance, real Safari/iOS contact behavior and human material approval require separate evidence. Consult the qualification report for the actual verdict; historical baseline results are preserved separately and are not current acceptance evidence.

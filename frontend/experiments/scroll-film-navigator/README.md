# Experiment 02 — scroll film & liquid navigator

An isolated interaction experiment for **0xmillennium**. [CONTRACT.md](CONTRACT.md) records the earlier remediation contract; the 12 October finalization assignment supersedes obsolete behavior. Current hold refinement, event timing, browser gate and new video evidence are in [HOLD.md](HOLD.md). The preceding navigator material checkpoint is [NAVIGATOR.md](NAVIGATOR.md). Approved charcoal delivery is recorded in [FOLLOWUP.md](FOLLOWUP.md). The first checkpoint is retained in [FINALIZATION.md](FINALIZATION.md), with historical qualification in [QUALIFICATION.md](QUALIFICATION.md). This does not integrate the experiment into the production homepage.

Native document scroll selects `round(progress × 191)` from one of two immutable 192-frame PNG sequences. There is no autoplay or playback clock. Film selection preserves normalized progress. The locked Triptych analysis informs liquid navigation and the outward reveal; its autoplay, four films and repeating hold are deliberately superseded by the task contract.

A hold grows translucent turquoise material over the **current** film, with a localized luminous edge and bounded film refraction. Input events settle the preceding hold phase before changing it, so pre-release growth time cannot become cancellation time. The gesture clock is separate from fluid transport and frame diagnostics. Early release erodes the stored spatial field; re-press resumes that field and retargets its center. At full coverage the correct incoming frame must be ready. An aperture then expands from the captured press center, showing the incoming film inside and tinted outgoing film outside, with a localized soft white boundary. A continuous physical press can commit once, then requires release. Earned readiness/reveal survives release; preparation failure times out through a controlled cancellation.

## Run and qualify

From `frontend/`, using the existing pinned toolchain:

```sh
npm run film:prepare         # deliberate full source hashes + 768 derivative hashes/decodes
npm run film:preflight       # cheap metadata/configuration/presence/size check
npm run film:dev             # http://127.0.0.1:4180
npm run film:typecheck
npm run film:test -- tests/wheel.test.ts
npm run film:browser:focused -- finalization.spec.ts -g 'Calibrated wheel'
npm run film:test            # experiment unit subsystem
npm run film:build
npm run film:browser:functional # six desktop/touch projects, no performance/soak
npm run film:browser:built   # consumes matching existing build; fails if missing/stale
npm run film:perf:smoke -- --project=chromium # 120 RAF steps, then a real hold/reveal
npm run film:perf:soak       # explicit extended workloads + 1,800-step endurance
npm run film:record:focused # short V1 desktop evidence, port 4192
npm run film:record          # explicit broader canonical desktop/touch campaign
npm run film:check           # frozen-source functional gate; build once if stale
node experiments/scroll-film-navigator/scripts/browser-preflight.mjs
node experiments/scroll-film-navigator/scripts/benchmark-prepare.mjs
node experiments/scroll-film-navigator/scripts/media-quality.mjs
FILM_BENCH_MODES=baseline-webp,directional2-webp,intra-vp9 FILM_BENCH_LABEL=benchmark-final node experiments/scroll-film-navigator/scripts/benchmark.mjs
node experiments/scroll-film-navigator/scripts/capture-transport.mjs
node experiments/scroll-film-navigator/scripts/material-study.mjs
node experiments/scroll-film-navigator/scripts/bfcache.mjs
```

FFmpeg with libwebp and the existing local Python Pillow/numpy/OpenCV tools are required for offline preparation; missing tools fail explicitly. Normal media consumption does not invoke them. Indexed VP9 and motion-interpolation studies additionally use the installed FFmpeg encoders/filters. No npm dependencies were added. Outputs are ignored under `frontend/test-results/scroll-film-navigator/` and `frontend/dist/scroll-film-navigator/`. Source PNGs remain ignored and immutable under this experiment's `assets/frames/` directory. No fallback MP4 or new creative film is used.

Normal development, builds, targeted browser tests and recordings **never invoke full preparation**. Full preparation writes a matching integrity receipt after validation; preflight compares source names/sizes/mtimes, output sizes and shared preparation configuration against that receipt. It detects ordinary missing/stale assets but does **not** detect same-size corruption or a malicious rewrite preserving metadata, and does not claim full integrity. Invoke `film:prepare` deliberately for complete integrity. Keep the receipt when reusing unchanged validated media; changed configuration or sources require full preparation. No FFmpeg invocation occurs during cheap preflight.

Final sequence: approve media → prepare/validate once → freeze source → `film:check` → separately justified performance, recordings and endurance. `film:check` consumes that validated publication and builds only if its source/media receipt is stale. Built tests never build or prepare. `film:browser` aliases functional coverage and `film:perf` aliases smoke; the historical extended performance test remains intact under `film:perf:soak`. Large ignored PNGs are unavailable in ordinary CI: existing CI type/unit checks use explicit validator fixtures, not canonical-media qualification. Full-media/browser qualification requires the real publication; missing media fails clearly. `execute.mjs` logs expensive runs to `finalization/execution.jsonl`, including timing, revision, fingerprint and result.

Prefer a test-owned server. `FILM_REUSE_SERVER=1` is an explicit local convenience for a stable known server; HMR, restart and interruption can invalidate a reused session. Do not run media preparation or other browser studies concurrently with performance measurements. Run full matrices only after source freeze, and never use full qualification or soak for routine edits.

## Scroll calibration and themes

The 4,200 px native range remains compact for touch, scrollbar and keyboard navigation. On the scene alone, `WheelCalibration` maps common 120 px or three-line vertical wheel units to `range / 191` pixels. Magnitude and a fractional remainder handle multi-unit, line and rapid input; one DOM event is not assumed to equal one detent. Nonquantized continuous input keeps its burst native for 180 ms. Horizontal, page, modifier/zoom input and controls remain native. Physical detents cannot be identified universally: a large isolated quantized trackpad packet can be mistaken for a wheel, and OS/browser wheel settings require calibration. Physical mouse verification remains distinct from synthetic browser wheel coverage.

The committed film owns the UI theme: robotic arm is dark; tree is light. Growing/canceled holds and mixed-film reveals retain the outgoing theme; commit exchanges the theme with the film. Semantic foreground, surface, secondary text, border, selected, focus and status tokens cover both themes. Film pixels are unchanged. Reload/history restoration use the same committed identity; color-scheme preferences do not override it.

## Media and ownership

Preparation hashes and validates all 384 source PNGs, including signature/chunk integrity, order and 3840×2160 dimensions. Version 4 treats robotic-arm delivery with a deterministic charcoal background and continuous plate-contrast matte, then encodes WebP q96 at 960×540 and 1600×900. Bright/high-contrast source pixels remain exact before encoding; low-contrast tones change. This is background treatment, not a complete semantic foreground extraction or detail recovery. The operator hash and installed Pillow/numpy/OpenCV/WebP versions enter the immutable preparation identity. Tree delivery remains Lanczos WebP q82 and reuses unchanged, hash-verified derivatives when possible. A SHA-256 version identifies the source inventory, encoder/version, options and preparation contract. Each of the 768 derivatives has a byte/hash/dimension inventory and a complete FFmpeg pixel-decode check. Fresh preparation verifies unchanged sources before publishing an immutable version directory, then atomically replaces the active manifest. A process lock prevents concurrent publication. Reuse validates the complete inventory and full decodes; missing or corrupt assets fail closed.

HTML contains a real responsive first-frame poster before JavaScript. Opening-frame decode gets priority; the next film is prepared only for an eligible hold or explicit selection. The tier is chosen once per session (960 through 700 CSS pixels, otherwise 1600) to avoid resize churn. Native image selection and bitmap decode use the same versioned asset URL.

The main decoded window owns at most 20 bitmaps and two concurrent loads. Direction and observed traversal speed adjust preparation priority; neither changes scroll progress. Aborted/stale completions cannot replace a newer target, and evicted bitmaps are closed. The two resident film textures pin their cache-owned bitmaps. Stable-size updates use `texSubImage2D`; film switching exchanges texture ownership. Draw submission is reported separately from captured compositor pixels and physical screen presentation.

A missing current target may preempt an unpinned speculative transfer after a film change, direction reversal or large jump. Ordinary traversal retains useful ahead transfers. An aborted transfer continues to occupy its bounded slot until settlement, including nonabortable bitmap decoding; cancellation cannot create unbounded concurrent ownership.

## Rendering and fallbacks

The primary renderer uses one WebGL2 context. GPU-owned velocity, dye, pressure, divergence and curl targets implement advection, pressure projection and vorticity. Bounded MacCormack correction preserves transported dye veins; movement deposits a distance-based dose and a narrower wake at higher speed. The shader composites film refraction, turquoise pigment, the persistent hold mask, aperture and white front. The hold mask is a bounded CPU signed-distance lattice, with growth/reveal rim channels and unchanged-state evaluation caching, uploaded only when changed; it is not regenerated from an analytic circle on re-press. GPU timing covers simulation, uploads and composition where the extension is available. Explicit development-only readback studies are excluded from ordinary rendering. Pointer samples form an ordered queue of at most 32 segments; forward-collinear segments may merge, and overflow ages out old segments without extending the final tangent. At most 32 arc-length deposits are submitted per frame. Tiny coalesced movements accumulate until a segment can be emitted. Submitted line dose is conserved across long batches; GPU pigment concentration remains bounded, so identical final mass across different aging/advection histories is not promised. A long preceding stall expires old pigment while retaining fresh pending deposits. Fluid transport takes one step capped at 1/30 second; excess elapsed transport time is deliberately discarded rather than accumulated into expensive catch-up passes. Pigment fading and expiry follow actual elapsed time. Diagnostics expose discarded transport time and dropped segments, so stalled-frame fidelity remains measurable.

Integrated graphics with shared system RAM is a normal target; a discrete GPU or separate VRAM is not required. The renderer uses the browser's default power preference, bounds backing resolution/DPR and simulation grids, reuses textures, skips inactive hold refraction and stops scheduling when settled. Decoded bitmaps and graphics allocations must be considered together on shared-memory systems. Normal qualification uses the browser's default graphics path; `FILM_SOFTWARE_GPU=1` explicitly selects SwiftShader for a separate regression run. Reports identify the actual backend and memory estimates; a software-rendered VM does not establish accelerated integrated-graphics performance.

Both navigator paths now share speed-sensitive deposit radii, directional wakes, irregular spatial pigment seeding, absorption and bounded local refraction. Slow radii range up to 48 CSS pixels; fast radii reach 14, with a longer directional wake. Deposited pigment channels move with the flow rather than being regenerated as animated screen noise. The optical slope uses interpolation to avoid displacement jumping between stored GPU texels.

Without usable float WebGL2 targets, clearly labeled Canvas2D compatibility mode transports three pigment channels through the existing CPU field, with pressure projection, vorticity confinement and clamped MacCormack correction. Dye is bounded to 192×256; velocity uses half those dimensions, and field arrays occupy less than 2.5 MB. This replaces the previous scalar-density flat ribbon. Canvas caches a resident film's pixel raster lazily, composites refraction only within the active pigment region at backing resolution, and reuses its output buffer. Those cached pixels/output buffers are included in renderer allocation estimates, invalidated on frame/resize changes and released on disposal. The compatibility hold uses cached masks, the same tint/rim constants and full-resolution film sampling along the boundary. Pigment interpolation shares corner weights and cached gradients rather than repeating seven coordinate calculations per pixel. Compatibility preserves the material identity at lower simulation resolution; its cost and fidelity do not establish primary-GPU or physical-device qualification. If graphics contexts cannot initialize, a semantic image mode preserves native scroll and film selection. Without JavaScript, the poster and semantic instructions remain readable.

Fine pointers retain the native cursor and add a tightly tracking core. Touch starts a hold immediately; movement beyond the gesture threshold cancels incomplete growth and leaves native pan/pinch available. Semantic buttons, the progress slider and visible focus provide keyboard access. Reduced motion removes fluid and spatial reveal while retaining film selection. Resize cancels transient hold/reveal back to the outgoing scene; visibility suspension preserves pigment briefly and ages it by real elapsed absence. History preserves selected film and native scroll. Context loss, abort and disposal explicitly release ownership.

Suspension discards unconsumed pointer samples, while preserving already-rendered pigment for elapsed-time aging. It also resets wheel-burst calibration. Development diagnostics distinguish reduced-motion suppression, compatibility material and an image-only graphics fallback; a numeric field update alone does not prove a visible navigator. Rounded event timestamps preserve distinct path coordinates, using the previous valid interval as a bounded velocity estimate; exact duplicate positions and backward packets remain ignored.

## Evidence limits

A running RAF loop or correct state index does not prove visible continuity. The candidate harness measures target generations and records canonical pixels; the integrated capture harness decodes a development-only source-identity band from actual compositor JPEGs and saves the timestamped frame sequence. Capture cadence is measured and can undersample 48 changes/second. Accelerated integrated-graphics performance, real Safari/iOS contact behavior and human material approval require separate evidence. Consult the qualification report for the actual verdict; historical baseline results are preserved separately and are not current acceptance evidence.

Focused visual capture accepts `FILM_REVIEW_RENDERER=basic` for compatibility and `FILM_REVIEW_LABEL=hold-refinement-compatibility` for a separate evidence directory. The default remains the GPU material in `finalization/review/`. These explicit options do not select production rendering.

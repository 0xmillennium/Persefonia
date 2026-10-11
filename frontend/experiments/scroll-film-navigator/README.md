# Experiment 02 — scroll film & liquid navigator

An independent interaction study for **0xmillennium**. This is neither the production homepage nor an integration layer. It imports nothing from Experiment 01 or the stale homepage prototypes. Java, JTE, production navigation and application assets remain outside its scope.

The supplied brief describes Triptych's observable liquid navigation/hold principles; the reference was not reverse engineered, and its current interaction was not independently browser-qualified here. No claim about its renderer or fluid algorithm is made. Our deliberate differences are native-scroll-only film progression, two films, reversible deterministic selection, and normalized progress retention on switching.

## Run and qualify

From `frontend/`, using the existing npm lockfile/toolchain:

```sh
npm run film:assets          # canonical source validation; fails on missing media
npm run film:prepare         # validate sources + reproducible derivatives (requires ffmpeg/libwebp)
npm run film:dev             # http://127.0.0.1:4180
npm run film:typecheck
npm run film:test            # asset-independent deterministic tests
npm run film:build           # isolated dist/scroll-film-navigator
npm run film:browser         # Chromium, Firefox, WebKit + phone/tablet touch configurations
npm run film:browser:built   # built artifact, port 4181, desktop + phone/tablet
npm run film:perf            # GPU-disabled Chromium desktop + phone workload measurements
npm run film:record          # desktop + native-touch browser recordings, isolated port 4182
npm run film:check           # every required local gate, including browsers
```

Install the repository-pinned Playwright browser binaries/dependencies if absent: `npx playwright install --with-deps chromium firefox webkit`. No new runtime or package dependency is added. Dedicated ports/output paths keep the experiment independent. Generated media, validation manifests, screenshots, recordings, test artifacts and performance reports belong under ignored `frontend/test-results/scroll-film-navigator/`. The build includes delivery images in ignored `frontend/dist/scroll-film-navigator/media/`; it does not bundle source PNGs.

The final harness uses the repository-pinned browsers without driver overrides or automatic retries. This host has intermittent native Linux WebKit/WPE startup crashes in its Mesa software graphics path. Tested llvmpipe/softpipe and headed GTK alternatives did not establish a reliable complete qualification environment; no workaround is retained in the configuration. The latest built matrix passed, but the complete local gate remains blocked. See [QUALIFICATION.md](QUALIFICATION.md) for exact results, preserved diagnostics and remaining functional/timing uncertainties. Linux WebKit is not physical Safari/iOS qualification.

`film:check` is intentionally a long qualification command: 98 development cases across six configurations, two 1800-RAF-step performance soaks, then 12 built-artifact cases. Workers run sequentially to avoid concurrent rendering workloads. Several stages also validate the approximately 1.5 GB immutable sources. The Vite compilation itself takes a fraction of a second on this host. Software graphics diagnostics can take longer and hit explicit test timeouts; this is distinct from application startup latency. Use `film:typecheck` and `film:test` for targeted source feedback, and retain `film:check` as the complete gate.

CI runs strict typechecking and deterministic tests only. The canonical user-provided media is approximately 1.5 GB and is deliberately ignored by the experiment's `.gitignore`. Per the user's instruction it stays local and is unavailable in a normal CI checkout. The existing CI job is not extended with an FFmpeg preparation and full media/browser workload budget. CI does **not** pretend to qualify media, builds or rendered browser behavior. Missing media fails local asset/build/browser qualification; no synthetic replacements or silent asset skips are used.

## Source media and derivation

`assets/frames/{robotic-arm,tree}/frame_00001.png` through `frame_00192.png` are the only canonical sources. Each contains exactly 192 consecutive 3840 × 2160 PNGs. Original files are read-only inputs; neither the implementation nor preparation script searches for an MP4, rewrites a source, changes ordering, or creates new creative motion.

`film:assets` verifies names/counts, PNG signatures, dimensions, required chunk structure, all chunk CRCs and source SHA-256s, one file at a time. It does not decode pixels just to inspect metadata. Checksums catch ordinary payload corruption; this is not a substitute for validating arbitrarily crafted compressed streams. Preparation runs the real PNG decoder in FFmpeg. A source validation manifest records hashes in ignored artifacts.

`film:prepare` uses Lanczos scaling, `libwebp`, quality 82, and two codec threads to produce **960 × 540** and **1600 × 900** tiers. Each source frame produces one derivative with the same file number. Quality 82 WebP encoding and resizing are lossy delivery choices; preserving every feature does not prove lossless pixels or perceptual equivalence. Human review must qualify perceived fidelity at the chosen resolutions. The source-hash/encoder-options manifest and complete derivative file inventory determine whether preparation can be reused. FFmpeg is a local build tool, never a browser or media-playback dependency. 53.57 MB of delivery assets cover both tiers: 14.90 MB for both 960 sequences and 38.67 MB for both 1600 sequences (decimal units). Only the selected tier is requested at runtime. At 1 Mbps, transferring a complete cold tier would require approximately 119 or 309 seconds before overhead; a few seconds cannot preload every frame at that rate. On-demand loading avoids making this whole transfer a startup requirement. Source changes deliberately invalidate the derivatives.

## Architecture and budgets

- `src/transport.ts`: pure mapping and authoritative film/progress state; bounded latest-target decode queue, abortable loads and explicit decoded LRU ownership. The loader cancels native image transfers by removing their source; already-started bitmap conversion keeps its decode slot until settlement. Temporary source images are released after conversion. WebKit navigation diagnostics reproduced access-control messages from blob-based decoding; native image-to-bitmap conversion avoids that intermediate URL.
- `src/loader.ts`: cancelable native image transfer followed by explicit bitmap ownership, with no teardown-sensitive blob URL.
- `src/input.ts`: timestamp-based velocity observation and pure interaction eligibility.
- `src/field.ts`: fixed-budget semi-Lagrangian density/velocity advection with viscosity, shear, dissipation and segment-based movement injection.
- `src/hold.ts`: independently testable continuous hold/cancel/commit/grace state machine.
- `src/material.ts`: organic signed-distance coverage model and matching GLSL compositing.
- `src/canvas-renderer.ts`: default full Canvas 2D compositor, two projected film slots, bounded CPU refraction overlay and native organic clipping.
- `src/renderer.ts`: optional WebGL 2 backend (`?renderer=webgl`), two reusable film texture slots, one field texture, shader/program disposal and optional disjoint GPU timer queries.
- `src/main.ts`: browser input, native-scroll observation, texture coordination, semantic controls, scheduling, fallback and lifecycle integration.
- `src/metrics.ts`: bounded timing rings and optional long-task observation.
- `tests/`: deterministic, integrated, pixel-difference, visual capture, release and performance qualification.

The default renderer preserves every material/hold feature without requiring WebGL or a physical GPU. Canvas 2D projects the matching film pair only on frame changes/resize, clips the organic contour natively, and resamples localized moving material into a reusable pixel overlay. Bilinear cell bounds skip exactly transparent spans; this reduces empty work without removing effects. Continuously interpolated material normals avoid nearest-cell refraction steps, with a reusable gradient buffer of at most 216 KiB. Refraction, shading, spatial contraction, residual re-press and repeated reveals remain available at desktop, tablet and phone sizes. An optional raw WebGL 2 backend implements the same scene model in one pass; it is opt-in because measured software WebGL on this host was substantially slower. Its initialization/context failure switches to the complete CPU compositor. Installed PixiJS adds no demonstrated value for this experiment's single compositing pass; a 3D runtime is unnecessary. The deterministic field is CPU-based and small, rather than a full-resolution GPU fluid solver: 144 columns, aspect-aware height capped at 192, six Float32 buffers and one reusable RGBA upload buffer (at most 756 KiB). This gives inspectable numeric invariants without floating-point render-target/extension assumptions. It is a viscous interaction material, not an incompressible-fluid scientific solver.

Initial resource budgets: 8 decoded images, 2 simultaneous fetch/decode slots, at most 2 primary targets and one nearby prefetch. Repeated readiness notifications retain the same intentional neighbor instead of canceling/restarting its prefetch. A superseded transfer is aborted; an already-started decode retains its slot until settlement and its obsolete bitmap is closed. The newest desired state is never owned by a decode callback. Renderer-resident keys are pinned separately from target keys. At the 1600 tier, the decoded LRU is at most 43.95 MiB, with up to two temporary native source images and two converting bitmaps (a conservative 21.97 MiB transient pixel allowance). At 960 those bounds are 15.82 MiB resident and 7.91 MiB transient. Native decoded-image cache retention remains browser-owned and is not included. The default renderer estimates its explicit native surfaces and pixel arrays separately; its updated two-slot estimate and observed resource samples are reported in QUALIFICATION.md. It owns no explicit WebGL textures. Browser-native 2D/compositor/driver copies are **not** included in these estimates. Temporary source images and their handlers are released after bitmap conversion; the application holds no compressed sequence cache. Browser HTTP/disk cache ownership is outside the app's decoded-memory estimate.

CPU film presentation caps DPR at 1.5 and the longer backing dimension at 1280; the localized refracting overlay has a 720 px long-edge budget. Optional WebGL caps its longer dimension at 2560. The session chooses 960 for initial widths ≤700 CSS px, otherwise 1600, avoiding decode churn during resizing. Features and film identity remain the same; delivery/sample resolution varies. These are experimental engineering budgets, not universal visual quality/performance claims. Canonical 4K files remain immutable and available for future delivery-tier work.

A slower connection preserves the last valid film while preparing requested frames; incoming reveal waits for a matching decoded pair. The experiment fetches frames on demand rather than requiring the whole sequence before interaction. `network.spec.ts` shapes cold-cache Chromium desktop, phone and tablet connections to 1 Mbps downstream plus 150 ms request latency, recording observed readiness and transfer bytes. It does not infer connection quality from device type or depend on the inconsistently supported Network Information API. A brief wait can make initialization acceptable; it does not increase sustained bandwidth or improve CPU rendering. Further codec/tier changes should compare visual fidelity, decode cost and measured transfer before adoption.

## Build/server and browser responsibilities

Predictable media work happens before delivery: source integrity validation, 4K image decoding for conversion, Lanczos resizing, WebP encoding and complete derivative inventory preparation. The build packages self-hosted ready-to-decode images; the browser never downloads source PNGs, runs FFmpeg, rescales the 4K sources or prepares an entire film. This moves the expensive source-processing workload to the developer/build/server side while preserving the approved sequence.

The browser retains only work that depends on current local input: frame decode, viewport projection, pointer/hold state, the bounded material field and localized compositing. Serving gesture-specific rendered frames would introduce round-trip latency and repeated image transfers; waiting at startup cannot remove those ongoing costs. A worker could redistribute browser work, but would not move it to the server or reduce total computation by itself. The present measurements do not justify adding a remote renderer or another runtime.

Future production delivery should serve the prepared tier files with content-versioned URLs and appropriate cache headers, optionally through a self-hosted cache/CDN under the repository's media/CSP policy. The experiment's development media handler deliberately uses `no-cache` for revalidation; it is not a production delivery configuration. Runtime Java/JTE/server changes and production deployment remain outside this task. The experiment preserves native semantic controls and full CPU-capable material behavior rather than substituting a server-streamed interface.

## Film transport and transitions

`progress = clamp(scrollY / (documentHeight - viewportHeight), 0, 1)` is the single progress authority. The real native scroll range is initially **4200 CSS px**, configurable in `#scroll-range`; it is independent of video duration/frame count. Keyboard, wheel, trackpad and native touch scroll use the same mapping. The semantic range control changes native scroll position, not an alternative frame clock.

`index = round(progress × 191)`, file number `index + 1`. Both exact endpoints are reachable; reverse input recovers the same frame. There is no time-based film progression, artificial wheel accumulation or scene inertia.

Desired, pending, decoded-available, renderer-resident, acknowledged-presented and draw-submitted state are distinct. During asynchronous load, the last valid frame remains visible. Superseded results cannot acknowledge the desired target. During reveal, a matching-index image pair is retained until the newest matching pair is available; mismatched film progress is never exposed. The incoming image is prepared before reveal. Missing/decode-failed frames retain valid presentation, show an explicit diagnostic and offer manual retry instead of an infinite hot retry loop. Each explicitly retried failed key receives a new query identity (`?retry=N`) to bypass native cached corruption; successful keys keep their delivery URLs. Retry bookkeeping is bounded by the 384 canonical keys and is released on disposal.

The hold creates an irregular signed-distance material with several interacting angular/spatial harmonics. Its radius contracts spatially on release, and its center/phase/morphology persist on re-press. The new film is revealed through this region with boundary refraction. At full coverage and matching-target readiness, exchanging slot ownership commits once; there is no generic opacity crossfade or uncovered image swap. Semantic scene buttons prepare the next frame and use the same organic reveal without requiring a held gesture. Reduced-motion or emergency plain-film mode prepares the new target and selects it directly.

## Navigator and hold calibration

A fine pointer has an immediate 6 px core, independent of the flowing field. Timestamp/displacement-derived velocity (capped at 4500 px/s) controls transverse width, longitudinal stretching and local directional shear. Each segment is resampled with at most 32 distance-weighted splats; at most 32 coalesced observations are processed per event. Nearby density regions merge, while transported/sheared/decaying regions can separate. Injection happens only on movement. Stopping leaves a temporary evolving trail that decays entirely; no stationary aura is injected. Exits reset velocity observation and hide the core while residual material dissipates.

The material both shades and refracts the underlying film with restrained field gradients and directional displacement. Hold and navigator share neutral glass/refraction language, but movement cannot change hold progress or commit a film.

Named initial values in `HOLD`:

| Parameter | Initial value |
| --- | --- |
| Completion duration | 2.8 s continuous hold |
| Countdown thresholds | 1/3 and 2/3 progress; labels 3 → 2 → 1 |
| Expansion response | exponential rate 10/s |
| Contraction rate | 0.72 progress/s |
| Center retarget damping | exponential rate 9/s |
| Grace | 0.55 s |
| Coverage guard | ≥0.997 of the 32 × 18 coverage sample grid |
| Touch movement threshold | 12 CSS px |
| Hold response clock | Actual visible elapsed time; exponential expansion/retarget responses remain stable at low callback rates |
| Incoming-frame readiness timeout | 12 s after entering COMMIT |

Hold expansion and retarget responses use real visible elapsed time rather than a capped integration clock; suspension clears gestures instead of catching them up on return. The field’s separate numerical integration remains bounded. These are our tunable experimental values, not verified reference constants. Coverage is additionally safe at progress 1 through a radius extending beyond the farthest viewport corner plus an 18% diagonal margin. Both compositors and the numeric coverage model use the same boundary function.

`IDLE → HOLDING → COMMIT → GRACE`; release before commit gives `CANCELING → IDLE`. Re-press interrupts contraction, keeps residual progress/expansion/phase, changes the target center and resumes its derived countdown. Commit needs duration, visual coverage and decoded readiness; displaying “1” alone cannot switch. Holding through grace starts another cycle; releasing in grace ends cleanly. Blur/cancel/capture loss releases; page suspension resets transient state. Readiness failure times out to contraction instead of permanent COMMIT.

## Input, composition and accessibility

Modality follows Pointer Events, not a blanket mobile flag. Mouse/pen moves generate navigator material and retarget a captured held center, including offscreen coordinates when delivered by the browser. A new press must be primary, button 0, inside the viewport, and outside semantic controls. Lost capture is handled explicitly, including engines that drop pending capture without sending a loss event; offscreen input is not invented.

Touch contact starts hold immediately. Moving beyond 12 px cancels hold and contracts existing material; touch does not inject desktop trails or continuously retarget the center. A new contact reuses residual state. `touch-action: pan-y pinch-zoom` and passive native scrolling preserve pan/zoom; no blanket `preventDefault` or `touch-action: none` is used. Chromium CDP touch injection exercises real browser gesture arbitration; WebKit automation's public tap API only qualifies the available tap/modality subset. Neither proves a physical touchscreen.

Portrait (aspect <0.9) intentionally contains the entire 16:9 source subject in the viewport center, with darkened, horizontally averaged edge-color extensions above/below. This avoids crop-only mobile treatment of the right-positioned arm/tree. Desktop/intermediate ratios use film-specific framing (arm 0.56, tree 0.50). The compositor and plain-film fallback use corresponding subject placement. Resize preserves film/progress; the field grid is rebuilt rather than stretched incorrectly.

Native labeled buttons, a labeled position range, live scene status, headings and visible focus remain outside the canvas. Coarse-pointer buttons and the range retain 44 px hit areas in either orientation. Header/footer consume safe-area insets with edge-to-edge viewport support. Countdown label placement uses cached rendered geometry to remain clear of controls without restricting the actual hold center. Repeated pending scene selections retain the existing reveal; choosing the outgoing scene cancels it, and selecting again resumes residual material. Completed hold input is distinguished from pending frame preparation in visible and live feedback. Reduced motion disables liquid displacement/core/coverage animation while retaining deterministic film scrubbing, selection and valid hold completion. No-JavaScript explains the experiment's dependency; this study hosts no essential production homepage content. System monospace and restrained labels intentionally make the film the primary evaluation surface, rather than inventing a homepage design.

## Lifecycle and diagnostics

Visibility/pagehide suspend rendering, release captured input and clear transient fields. Pageshow/BFCache restoration reads durable film selection and native scroll position, rebuilds the desired target and does not start a hold transition. History state stores selected film and a diagnostic scroll snapshot with `replaceState`, preserving other history properties and deduplicating/throttling ordinary writes to avoid browser rate limits; film changes do not create synthetic navigation entries. Native browser history/reload restoration owns scroll position; a potentially stale throttled snapshot never overrides it. Suspension cancels pending persistence and aborts pending frame transfers without discarding useful decoded residency. An outgoing/hidden document never writes history, because WebKit can already expose the destination entry during traversal. Popstate reconstructs the scene. Actual BFCache admission is browser-dependent; back/forward/reload recovery is tested separately.

Optional WebGL loss switches to the complete CPU compositor. Context restoration can rebuild the optional backend. Only failure of both full renderers uses the emergency plain-film canvas, with usable controls and a visible diagnostic. Disposal cancels RAF, aborts/removes listeners, aborts transfers, closes cache resources (including late decodes), deletes textures/programs/queries and disconnects timing observation. Only one presentation renderer is active.

The optional dev Diagnostics panel updates at most 4 Hz. `window.__filmExperiment` exists only under Vite DEV, for snapshots and explicit lifecycle/metric/controlled-visual-clock test operations; production artifacts must not expose it. Ordinary measurements keep diagnostics hidden. Scheduler work stops once hold/field work and decode updates settle.

Metrics distinguish input CPU, fetch/decode, tick JavaScript (including CPU material work), draw submission (CPU rasterization plus native 2D calls on the default backend), active RAF intervals, request-to-draw submission, long tasks, resize, cache events and estimated resources. The `frameDrops` counter flags active application RAF gaps greater than 25 ms; it does not count physical display drops. Render-frame p50/p95/p99 in this environment refers to reported CPU/RAF samples, **not** photon latency. Optional disjoint GPU queries measure the draw pass when supported, with at most four outstanding queries. Neither draw submission nor an updated DOM attribute proves visible presentation latency. Inspect real screenshots/recordings and a browser performance trace for actual raster/compositor behavior. No universal 60 Hz pass is inferred from software rendering.

See [QUALIFICATION.md](QUALIFICATION.md) for observed results, evidence locations, limitations and the final verdict. Production readiness additionally needs human visual approval, representative physical desktop/tablet/phone CPU and browser measurements, real touch/trackpad tests, browser zoom and continuously resized composition review, actual BFCache qualification and final-homepage integration under a separate task. This experiment's tests do not qualify the Java/JTE production website.

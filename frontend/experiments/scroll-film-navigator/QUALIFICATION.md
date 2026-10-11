# Experiment 02 remediation qualification

Finalized 2026-10-12 (Europe/Istanbul) at the user’s instruction to stop testing. This is the isolated experiment, not production homepage integration. No additional tests were run to finalize this report; existing command logs and artifacts were read. Unfinished gates remain explicit. The supplied remediation contract supersedes incoming-film growth and repeating-hold behavior in the preserved baseline.

## A. Repository state

Initial and current branch: `feat/scroll-film-navigator`; HEAD: `a650818f71f658668f34d90b76c37f36e6101bee`. Changes remain in the working tree. No commit or push was performed. No npm dependency, package script, CI, Java/JTE, production asset, application or Experiment 01 change was introduced. The user's existing root `.gitignore` addition for `sample-site-analysis` was preserved.

Changes cover experiment HTML/poster/CSS, Vite serving/build, both Playwright configurations, hold/controller/transport/metrics, GPU and basic/image renderers, persistent hold material, offline validation/preparation and study/capture scripts, targeted tests, README, contract and this report. The changed-file list is below; the frozen runtime/config/test/script fingerprints are in `remediation/final-source-cutoff.json`.

Changed files, relative to the experiment:

```text
CONTRACT.md
QUALIFICATION.md
README.md
index.html
playwright.config.ts
production.playwright.config.ts
scripts/assets.mjs
scripts/benchmark-prepare.mjs
scripts/benchmark.mjs
scripts/bfcache.mjs
scripts/browser-preflight.mjs
scripts/capture-transport.mjs
scripts/delivery.mjs
scripts/material-study.mjs
scripts/media-quality.mjs
scripts/prepare.mjs
scripts/record.mjs
src/canvas-renderer.ts
src/gpu-fluid.ts
src/hold-material.ts
src/hold.ts
src/image-renderer.ts
src/input.ts
src/main.ts
src/material.ts
src/metrics.ts
src/renderer.ts
src/style.css
src/transport.ts
tests/acceptance.spec.ts
tests/built.spec.ts
tests/composition.spec.ts
tests/delivery.test.ts
tests/gpu.spec.ts
tests/hold.test.ts
tests/motion.test.ts
tests/network.spec.ts
tests/performance.spec.ts
tests/resilience.spec.ts
tests/support.ts
tests/transport.test.ts
tests/visual.spec.ts
vite.config.ts
```

All 384 source PNG inventories match the preserved baseline exactly, including SHA-256 and 3840×2160 dimensions. Asset validation checks signatures/chunks/CRCs without pixel decoding. Fresh preparation additionally decoded every source through FFmpeg and fully decoded all 768 resulting derivatives before publication. Current delivery version: `04e06c899fd6b8167c4bfce861ef8e3e440289f20a7b55bc4c1c78350090db52`; source inventory hash: `b51a02afed8301ea4a1b6dede1669ba7c183175776c9499903ccc75bd7baf100`. No MP4 substitute or new creative source was used.

## B. Architecture decisions

Selected transport: versioned, original-time WebP q82 sequences, 960×540 and 1600×900. The image path is portable across all three engines and keeps exact source index ownership. Decoder concurrency remains two after comparing two, three and four slots; the main decoded window expands from eight to twenty images and uses direction/velocity priorities. The scheduling sweep demonstrated substantially better reverse/2× readiness with the larger directional window; increasing slots alone did not improve it. The final isolated benchmark and integrated capture are reported below separately.

| Candidate | Canonical-source evidence | Decision/limits |
| --- | --- | --- |
| A: WebP image sequence | Both films, all 192 frames, two delivery tiers, complete hashes and pixel decodes | Selected; AVIF encoder/muxer unavailable in installed tools, so no invented AVIF comparison. |
| B: indexed all-intra VP9 IVF / WebCodecs | Both 192-frame films at 960×540; packet offsets, lengths, timestamps and hashes; full FFmpeg decode; actual browser random/bidirectional accesses | Rejected for this measured implementation: larger transfer, worse cold/random readiness and less portable decoder API. This does not establish that every compressed-media implementation loses. |
| C: offline MCI interpolation | Four actual robotic-arm/tree source spans, 640×360, 24→48 fps optical flow; original-time and added frames inspected separately | Rejected: thin robotic lines show interpolation artifacts; bounded low-resolution studies do not qualify an entire interpolated film or demonstrate a necessary benefit. No study frame enters delivery. |

960 delivery: robotic arm 3,051,008 bytes and tree 11,848,676 bytes; VP9 candidates approximately 5.19 MB and 20.52 MB. Three original-time quality probes per film gave WebP PSNR 47.08–48.74 dB for the arm and 42.43–43.21 dB for the tree; VP9 48.33–48.85 and 46.03–46.55 dB. VP9's tree probes have higher PSNR at greater transfer cost. These are sample technical measurements, not equal-quality encoding claims or full-sequence perceptual approval. Both WebP tiers together total 53,565,252 bytes; only the session tier is fetched.

Offline preparation uses source/encoder/options identity, a process lock, temporary output, complete derivative inventory and FFmpeg decode, unchanged-source revalidation, immutable-directory publication and atomic active-manifest replacement. Reuse revalidates every hash, container, dimension and complete decode. Corruption fails closed. HTML delivers the real first-frame poster before enhancement; only eligible hold/selection starts preparing the other film.

The primary renderer owns one WebGL2 context, two resident film textures, a changed-only hold texture and ten fluid targets. Float velocity advection, divergence, twelve Jacobi pressure passes, projection, vorticity and bounded MacCormack dye correction keep the fluid on the GPU. Velocity's longest side is 192; dye's is 512. An ordered 32-segment queue and at most 32 arc-length deposits avoid event-count dosing and preserve reversals; shear and advected pigment ratios create veins and curls. No ordinary frame performs a CPU fluid pixel loop or GPU readback. Explicit development-only readback studies remain outside ordinary performance evidence. GPU timer queries, where supported, encompass simulation, upload and composition and remain bounded to four.

A stalled frame advances fluid transport by at most 1/30 second while fade/expiry use actual elapsed time. Excess transport time is discarded and reported; this bounds workload but sacrifices transport fidelity during long stalls. Unlimited catch-up was not introduced without hardware evidence. Queue overflow drops old non-collinear segments explicitly instead of inventing an extrapolated path.

The hold uses a persistent 192-wide signed-distance lattice with transported structure. Growth dilates it, cancellation erodes it, and retargeting advects its existing morphology. The reveal captures the effective center once, monotonically expands an aperture, preserves tinted A outside and untinted B inside, and adds a localized white front. Pair readiness belongs to the latest native scroll target. One press commits once, then stops animating until release.

Cache ownership is bounded to twenty decoded bitmaps / two loads. Eviction and stale completion close their bitmaps; resident film keys pin at most two images. Same-size texture changes use `texSubImage2D`. Retained bitmap-cache estimates are 115.2 MB desktop and 41.472 MB phone. These are not total decoded-memory ceilings: two pending decodes, conversion intermediates, the native poster, CPU hold buffers, graphics/backing allocations and browser/driver overhead add to the shared system-memory budget. Primary fluid targets are already included in renderer bytes and must not be counted twice. Linux process PSS measurements are reported separately from ownership estimates. Basic Canvas2D preserves film/hold access with labeled reduced fidelity; failed graphics contexts select a semantic decoded image. Reduced motion preserves controls and scroll without spatial effects. No-JavaScript retains a visible responsive poster and semantic instructions.

Fluid implementation references: [GPU Gems: fluid dynamics](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu), [GPU Gems 3: advection and bounded correction](https://developer.nvidia.com/gpugems/gpugems3/part-v-physics-simulation/chapter-30-real-time-simulation-and-rendering-3d-fluids). These explain techniques; rendered browser evidence determines this experiment's qualification.

## C. Behavioral qualification

Scroll mapping, reverse/endpoints/freeze, asynchronous race safety, progress-preserving selection and bounded cache ownership are covered by executable tests. Pixel fixture tests separately prove current-only growth, full viewport tint, center-first B, outward region ownership, localized white rim and clean B completion. Canonical footage remains the source in ordinary browser/recording/performance work.

Cancellation and re-press retain both progress and the actual lattice, with coherent center retargeting. The final continuous automated 15-second wall-time contact recording was not regenerated. Earlier recordings are archived and do not qualify the final source. Earned readiness/reveal survives release; failure cancels through a bounded 12-second path. Touch movement cancels incomplete growth and preserves native pan/pinch. Native held-touch injection is Chromium CDP evidence; WebKit's tap subset is not real iOS hold qualification.

Resize intentionally cancels transient growth/reveal to the outgoing scene, resets pointer sampling and rebuilds bounded targets. Short visibility suspension preserves pigment; actual elapsed absence decays/expires it. Reload/history retain film and native scroll. DPR/page-scale changes, capture loss, blur/cancel, context loss/restoration, request aborts and disposal are separately exercised. History restoration is not automatically proof of BFCache admission.

Behavior is classified separately:

| Concern | Qualification scope |
| --- | --- |
| Scroll correctness | Exact native-geometry indexing, endpoints, reverse, freeze and asynchronous ownership are executable checks. |
| Scroll smoothness | Captured compositor sequence and callback/readiness measurements are distinct from correctness; representative 60 Hz integrated graphics remains unverified. |
| Navigator quality | Velocity response, persistence, advection, decay and input geometry have automated checks. Ebru topology and aesthetic quality require canonical visual review. |
| Hold cancellation/resumption | Stored lattice identity, residual progress, coherent retargeting and countdown continuity are checked independently. |
| Full tinted coverage | Pixel fixtures verify current A only and complete coverage before readiness/reveal. |
| Outward reveal | Pixel fixtures verify captured-center aperture, monotonic outward ownership, tinted A outside, B inside and localized white rim. |
| One press / one transition | Deterministic controller tests and earlier automated wall-time contact evidence exercise the latch; final-source contact recording remains unexecuted; a distinct released/re-pressed contact earns the next transition. |
| Touch semantics | Chromium CDP exercises native contact arbitration/pan; WebKit emulation has a tap subset. Emulation does not prove physical Safari/iOS behavior. |
| Lifecycle | Resize, visibility aging, reload/history, interruption, graphics recovery and disposal have explicit checks; actual BFCache admission has an implemented but unexecuted built probe. |
| Accessibility | Semantic controls, visible focus, keyboard navigation, reduced motion and built no-JavaScript/failure content are tested. Manual assistive-technology and physical-device review remain separate. |

Navigator morphology, captured continuity, physical-device accessibility and timing remain distinct from numeric solver/state correctness. Human visual approval is pending.

## D. Test evidence

Artifact root: `frontend/test-results/scroll-film-navigator/` (ignored). The final frozen-source `npm run film:check` exited 0 (21:04:42–21:24:32 UTC). Its actual chain executed `npm run film:typecheck`, `npm run film:test`, `npm run film:assets`, `npm run film:build` (including `film:prepare`), `npm run film:browser`, and `npm run film:browser:built`. Typecheck and build passed; unit tests passed 50 in seven files; all 384 source PNGs and all 768 reused derivatives passed their respective integrity and complete-derivative-decode checks. Development: 99 passed, 35 intentionally skipped, zero failed/flaky, 18.8 minutes. Built: 24 passed, zero failed/skipped, 49.5 seconds. Retries remain zero.

| Development project | Viewport / DPR | Passed | Skipped |
| --- | --- | ---: | ---: |
| WebKit | 1200×800 / 1 | 17 | 5 |
| WebKit touch | 390×844 / 2 | 12 | 10 |
| Chromium | 1200×800 / 1 | 20 | 3 |
| Firefox | 1200×800 / 1 | 17 | 5 |
| Chromium touch | 390×844 / 2 | 17 | 6 |
| Chromium tablet touch | 768×1024 / 2 | 16 | 6 |

All six corresponding built projects passed four cases each. Skips distinguish desktop fine-pointer/pixel fixtures from touch cases, Chromium-only CDP operations and unavailable protocol coverage; they do not remove a browser project or count unexecuted physical Safari checks as passes. Performance-only cases run in the two configured Chromium projects.

Observed targeted gates: `npm run film:typecheck`; `npm run film:test` — 50 passed in seven files. Production `npm run typecheck` and `npm run test:unit` — 216 passed in nineteen files. GPU packet/advection/decay plus actual elapsed suspension: three desktop engines, six passed. Input/resize/DPR audit regressions: six passed, three modality/protocol skips. Pixel ownership/rim: all three desktop engines passed.

Fresh preflight: five WebKit launches plus Chromium and Firefox passed. After an intermittent WebKit page crash during application navigation, a new 28-case investigation passed minimal, GPU app, basic app and no-JavaScript app modes (five WebKit launches and one Chromium/Firefox each per mode). One subsequent initial-navigation crash occurred in the full matrix. Its cause is not established by successful minimal pages. No browser project was removed, no native crash was reclassified as a pass, and automatic retries remain zero.

Prior complete and interrupted results are retained independently:

- First complete gate: development 96 passed, 35 skipped, 3 failed. Failures were WebKit navigation crash, desktop soak timeout and a touch re-press assertion that used a fixed delay despite slower real elapsed dispatch. The last assertion now accounts for observed elapsed cancellation; the soak still requests 1,800 steps, with a measured finite 900-second timeout and workload checkpoints. No workload was shortened to obtain a pass.
- A responsive follow-up was interrupted after 40 passes, 16 skips and one setup failure: it started a hold before the asynchronous resize handler finished. Waiting for the actual viewport and idle state resolved the targeted all-project checks (9 passes, 3 modality skips).
- A subsequent complete development matrix passed 99 with 35 skips; its built stage passed 23 and failed one WebKit no-JS case involving an additional automation context. The test now uses the normal fixture context with JavaScript disabled and preserves the project's viewport/DPR. The standalone built rerun passed 24; it did not make the earlier failing `film:check` green.
- The 20:48 UTC-cutoff rerun was deliberately interrupted for the bounded Local Codex findings: 65 passed, 23 skipped, one interrupted, 45 not run. It is not a passing full gate. A new GPU delayed-step setup initially passed WebKit and failed Chromium/Firefox because separate tool tasks allowed a zero-delta RAF to consume input; atomic injection plus advance passed all three engines. The assertions still require fresh pigment after the delayed step and full expiry afterward.

Exploratory failure evidence is retained: stale cache-size expectations, incorrect frozen-clock test setup and asynchronous resize-event timing were corrected without changing the asserted contract. Source edits during an earlier live test caused HMR navigation; that run is invalid qualification evidence. Built no-JavaScript assertions initially targeted `noscript`, which Playwright's text matcher excludes; the actual visible paragraph is asserted instead. The no-JavaScript loading banner was also corrected to be hidden until enhancement starts.

## E. Visual evidence

Final-source visual captures exist under `visual/{project}/` and pixel-composition evidence under `composition/`. Final continuous canonical desktop/mobile recordings were not regenerated. Older recordings are archived under `remediation/pre-final-recordings/`; original baseline evidence is under `remediation/baseline/`. These older artifacts do not qualify final-source continuity.

The final isolated benchmark completed and wrote `remediation/benchmark-final/report.json` and candidate recordings. The integrated transport-capture process was stopped at the user’s request; `transport-capture/` contains partial artifacts and must not be treated as a completed gate. The final recording, checkerboard material-study and actual BFCache stages were not run. No final pixel/human approval is claimed. Quality/interpolation studies remain under `remediation/candidates/`.

The locked analysis was read from `frontend/experiments/sample-site-analysis/triptych-current-interactive-page-analysis-locked.md`. OpenDesign Local Codex supplied an inspected midpoint critique and a partial material study. The study run ended at its usage limit; its native GLES replay captures and actual source were inspected, rather than trusting the run status. The generated page was not adopted wholesale. Native GLES replay is not browser qualification. Current shader refinements were then tested in the real browser.

A subsequent independent Local Codex source review completed at run `876b5194-c235-4017-9426-7b1b219040fd`, against the 20:26 UTC cutoff. Its actual report is retained at `remediation/opendesign-final-source-review.md`. This review inspected source and metadata, explicitly not final rendered pixels. Its reversal counterexample led to the ordered queue/arc-length fix and four new unit regressions; the transported B vein channel now contributes bounded contrast. Recording fingerprints now cover all runtime TS/CSS, HTML, Vite/TypeScript configuration and the recording script. Report paths and automated-contact wording were corrected. The transport-time cap is an explicit measured tradeoff, not silently described as full real-time evolution. Performance finalization now writes completion only after the separate trace settles; its final observed result is reported below. A bounded follow-up (`5beed80e-9d0b-4f3d-aaec-d7cfdb80fdc0`, report `remediation/opendesign-bounded-followup.md`) inspected four files and an input excerpt, within a 700-word report limit. It found three additional counterexamples: subthreshold coalesced movement disappeared, long batches attenuated submitted dose, and a preceding stall could expire fresh pending input. The final corrective pass accumulates subthreshold distance with separate monotonic timestamps, conserves pre-render line dose while retaining the independent GPU concentration bound, and expires old targets while preserving fresh deposits. Fresh pigment is added after aging the existing field. Pure input regressions and an actual GPU delayed-step regression cover the counterexamples. Submitted-dose conservation is not an assertion of identical rendered mass across temporal advection, fading or concentration saturation. No generated framework, engine or creative media was adopted.

## F. Performance evidence

Host: Linux/Zorin 17 Ubuntu 22.04 in VMware, four logical CPUs labeled Intel i7-12700H, approximately 10 GB RAM, Node 24.16.0, FFmpeg 4.4.2. Browser versions: Chromium 143.0.7499.4, Firefox 144.0.2, WebKit 26.0. Chromium uses ANGLE SwiftShader Vulkan; Firefox reports Mesa/llvmpipe. Linux WebKit's exposed Apple GPU label is not evidence of physical Apple hardware. The guest exposes VMware SVGA II. Fresh default-headless, explicit SwiftShader and headful Chromium probes all report SwiftShader; this establishes the guest's actual path, not the host's hardware. No accelerated physical-device path or connected phone has been qualified.

Integrated graphics and shared system RAM are normal deployment targets, including phones and ordinary work desktops. No discrete GPU or separate VRAM prerequisite is established. The renderer requests the browser's default power preference, uses bounded grids/backing resolution, reuses film textures and avoids inactive hold-gradient fetches. Reports account for decoded images and graphics together rather than assuming separate memory pools. Normal qualification now uses default graphics selection; `FILM_SOFTWARE_GPU=1` selects an explicit software regression. `remediation/graphics-path.json` records the three actual path probes. The primary GPU API is active; it is not a GPU-disabled test. Ordinary performance work keeps diagnostics hidden. CPU target-to-decode, upload and draw-submission timings, GPU queries, callback distributions, browser trace events, capture intervals and physical scanout are separate measurements. The in-app `frameDrops` counter counts callbacks exceeding its interval budget; it is not a count of measured display drops. Unsupported GPU timer queries mean duration is unavailable, not zero. Trace/screenshot/state inspection adds overhead and is excluded from ordinary cost claims. Soak duration is the observed wall duration of 1,800 requested RAF steps, not an invented fixed-time interval. Candidate video is 25 fps and cannot prove 48 changes/second. Integrated capture records its actual cadence and retained source identities.

Process-resource sampling uses Linux `/proc` for Chrome browser, renderer, GPU-service, utility and zygote processes. CPU is expressed as percent of one core (400% corresponds to four fully occupied cores). PSS apportions shared pages; summed RSS double-counts shared mappings and is not the preferred total. PSS refreshes every five seconds. The initial observer incorrectly assumed Chromium's first command-line entry contained only an executable path; Chromium had rewritten it to include flags. Correcting prefix normalization made processes visible. Coverage therefore starts partway through the standalone desktop workload: startup peak memory was not measured. The old empty observations are preserved as a failed observer attempt, not reported as zero RAM. Process measurements include browser/driver overhead beyond application-owned caches; they are guest RAM observations, not dedicated VRAM or accelerated integrated-GPU residency measurements.

The phone performance project is a 390×844, DPR-2 touch-enabled context, with a 585×1266 bounded drawing buffer. Its synthetic fine-pointer fluid stress does not assert that touch injects pigment; native touch tests separately require no new fluid. No physical phone thermal/energy qualification is claimed.

The standalone `npm run film:perf` finished before shutdown: 2 passed in 9.8 minutes. Both reports have observed COMPLETE status. These tests passing establishes completion and bounded ownership, not acceptable smoothness. Desktop dense-fluid callback intervals were p50 116.6 ms / p95 150.1 ms; simultaneous scroll/fluid p50 116.7 / p95 150.1 ms. Desktop soak active intervals were p50 116.7 / p95 166.7 / maximum 399.9 ms. Touch-context soak active intervals were p50 83.3 / p95 116.7 / maximum 633.2 ms. The actual software-rendering experience is far below 60 Hz during fluid activity. Representative integrated-graphics/physical-phone performance remains unverified; a discrete GPU is not required.

The process sampler completed 446 nonempty samples over approximately 461.8 seconds, spanning part of desktop and the subsequent touch-context run. Observed peak summed Chrome PSS was 699,128,832 bytes (approximately 667 MiB); peak CPU was 367.2% of one core. This excludes initial desktop startup and Node/Vite. The retained image-cache budgets alone (115.2 MB desktop / 41.472 MB touch) substantially understate total browser shared RAM usage. Sampling does not establish a physical-device memory ceiling or a leak-free long-duration deployment.

The final isolated benchmark completed at 21:37:17 UTC. Directional WebP warm 2× reverse readiness was 100% in the sampled target generations, with 191 distinct frames for each film and maximum held intervals of 41.2 ms (arm) / 37.4 ms (tree). However, the alternating-target workload achieved only 32.37% / 21.99% readiness, with maximum holds of 366.8 / 428.4 ms. These are observed transport limitations, not a passed rapid-jump smoothness claim. Baseline warm 2× reverse readiness was 23.65% for each film. VP9 arm warm 2× reverse readiness was 82.16%; tree 99.17%. Local HTTP and 25 fps recordings do not qualify physical scanout, 48 fps visible continuity or mobile network conditions.

## G. Contract compliance matrix

PASS below is scoped to executed software/browser evidence, not every physical device. The following statuses are final for the stopped work session; incomplete stages retain their limits. Physical-device gaps do not waive observed failures in this guest.

| Requirement | Status | Evidence / limit |
| --- | --- | --- |
| FT-01: Canonical asset contract | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-02: Deterministic mapping | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-03: Endpoints | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-04: Reverse correctness | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-05: Inactivity freeze | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-06: Async race safety | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-07: Progress-preserving film switch | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-08: Directional prefetch | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-09: Velocity-aware preparation | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-10: Bounded resource ownership | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-11: Corrupt/missing media | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-12: 1×/2× visible continuity | NOT VERIFIED | Integrated captured sequence and representative hardware remain open. |
| FT-13: Cold-start behavior | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-14: Rapid reversals | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| FT-15: Derived media correspondence | PASS | Source/transport tests and browser evidence; physical limits above apply. |
| LN-01: Pointer-core tracking | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-02: Velocity response | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-03: Spatial persistence | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-04: Natural dissipation | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-05: No permanent aura | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-06: Advection | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-07: Topology | PARTIAL | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-08: Film deformation | PARTIAL | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-09: Input resilience | PASS | Controlled primary GPU studies; topology/material acceptance remains open. |
| LN-10: Material-quality evidence | PARTIAL | Controlled primary GPU studies; topology/material acceptance remains open. |
| PH-01: Eligible hold start | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-02: Current-film-only tint | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-03: Countdown continuity | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-04: Full coverage | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-05: Early release | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-06: Residual re-press | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-07: Center retargeting | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-08: Target readiness | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-09: Center-originating reveal | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-10: Outward progression | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-11: Correct region ownership | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-12: White boundary | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-13: Clean final state | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-14: Exactly one commit | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-15: Separate press required | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-16: Slow incoming media | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-17: Failed incoming media | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-18: Release during reveal | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-19: Touch movement cancel | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-20: Touch residual re-press | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |
| PH-21: Input interruption | PASS | Controller/lattice tests, pixel ownership and browser input evidence; automated matrix passed; physical/human limits remain. |

| Additional requirement | Status | Closure evidence |
| --- | --- | --- |
| Accelerated integrated/shared-memory desktop / 60 Hz / warm 2× | BLOCKED | Actual accelerated integrated graphics and captured presentation/latency under the specified workload. A discrete GPU or dedicated VRAM is not required. Software results remain separately recorded. |
| Physical Safari/iOS stationary hold, pan, pinch, capture and orientation | NOT VERIFIED | Actual device interaction recording and failure/lifecycle checks. |
| BFCache admission | NOT VERIFIED | A recorded `pageshow.persisted` restoration, independently of ordinary back/reload correctness. |
| Human ebru material, mobile framing and reveal approval | PARTIAL | Inspect canonical recordings and controlled comparisons on a representative display. |
| Final complete browser / built gate | PASS | Frozen-source `film:check` exited 0: 50 unit, 99 development passes / 35 skips, 24 built passes. Additional diagnostics and physical gaps remain independently classified. |
| Separate performance gate | PASS | 2 completed tests, 9.8 minutes; both reports COMPLETE. Measured software fluid pacing is poor and is not a smoothness pass. |
| Pending input across long suspension | NOT VERIFIED | Source inspection identifies a risk: pending samples have no freshness timestamp; suspension preserves them and resume can treat old queued input as fresh. The focused runtime probe was not executed. |
| Resize during growth and earned reveal | PASS | Explicit cancellation to outgoing film; responsive intermediate-width checks and countdown bounds. |
| Orientation / mixed pointer and touch | PASS | Chromium CDP touch; no new touch pigment, hover core suppressed; physical-device coverage remains separate. |
| DPR / page-scale change | PASS | Chromium CDP DPR and visual page-scale overrides plus fixed-DPR touch contexts. This is not browser-toolbar zoom qualification. |
| Native browser-toolbar zoom | NOT VERIFIED | Actual browser zoom interaction and mapping/input inspection; page-scale emulation alone does not close this row. |
| Short/long visibility absence | PASS | Executed 150 ms and 4.4-second suspension checks: residue decays in real elapsed time and expires fully. These tests age already-rendered residue; pending input before suspension is excluded and remains an identified risk. Actual OS app switching remains a physical-device check. |
| Reload / native back restoration | PASS | Development native reload and built native history traversal; selected film and browser-owned scroll restored. BFCache admission is a separate row. |
| Pointer cancel / blur / lost capture | PASS | Browser interruption checks; capture loss and pointer cancel are exercised explicitly. |
| GPU loss / restoration | PASS | Context-loss extension checks with basic fallback and reconstructed bounded GPU ownership. Missing extension is an explicit capability skip. |
| Abort / dispose / late completion | PASS | Loader/cache regressions and browser disposal: obsolete media closed, requests aborted, RAF stopped and renderer released. |
| Semantic/no-JS/graphics-failure content | PASS | All six built projects test visible poster/instructions and usable semantic image selection. |
| Keyboard / reduced motion | PASS | Native keys, semantic control operation, visible focus CSS and reduced-motion film access. |
| Manual assistive technology | NOT VERIFIED | Human screen-reader/navigation inspection; automated semantic checks do not prove it. |
| Source/media identity | PASS | Exact baseline source inventories; fully decoded, hash-validated versioned derivatives. |
| Production isolation / dependency boundary | PASS | Scoped working-tree inspection and production typecheck/unit regressions. |

## H. Final verdict

**NEEDS_REWORK.** The final complete automated gate passed, but completion of the task contract is not established. Software fluid pacing is poor; alternating-target benchmark readiness is low; a pending-input suspension ordering risk remains unverified; final continuous recordings/material comparison/BFCache evidence are missing; representative integrated/shared-memory hardware and human visual approval are unqualified. No production integration or deployment is authorized by this result.

Recommended next work, only if resumed: (1) inspect and resolve pending-input aging at suspension boundaries with one focused regression; (2) assess fluid cost and cache/shared-RAM tradeoffs on representative integrated graphics, using a short workload; (3) inspect existing captures with a human before further visual changes; (4) generate missing recordings and BFCache evidence once source is settled. Run targeted checks for each change, then at most one final complete gate. Do not repeat complete qualification for routine single-line changes.

Testing and queued qualification were stopped at the user’s request. The controller was terminated and the active transport-capture child was stopped. The completed performance/benchmark commands are retained as completed; partial capture is not relabeled a pass.

# Experiment 02 qualification

This report covers the independent `frontend/experiments/scroll-film-navigator/` implementation. It does not qualify production homepage integration, the Java/JTE application or Experiment 01. Measurements are local observations, not universal device guarantees.

## A. Implementation

The entry point is `index.html` / `src/main.ts`. Native document scroll maps to `round(progress * 191)`. `loader.ts` uses cancelable native image transfer followed by explicit bitmap conversion without internal blob-URL decoding; temporary source images/handlers are released at settlement. `transport.ts` owns film/progress and asynchronous presentation; its eight-image LRU allows two concurrent fetch/decode slots, aborts superseded transfers and closes obsolete/evicted bitmaps. The same-index pair is prepared before revealing the incoming film. Last valid presentation remains during loading or a controlled decode error; manual retry targets current progress.

`input.ts` observes physical pointer velocity; `field.ts` provides bounded viscous density/velocity advection, directional shear, segment injection, spatial persistence and complete decay. `hold.ts` owns continuous countdown, growth, spatial cancellation, residual re-press, damped center retarget, readiness/coverage-gated commit and repeat grace. `material.ts` defines shared irregular coverage. Default `canvas-renderer.ts` uses two projected films, native organic clipping/rim refraction and a bounded reusable CPU displacement overlay with continuously sampled normals (an additional gradient buffer ≤216 KiB). Transparent cell-span elimination skips mathematically zero contribution without changing visible effects. Optional `renderer.ts` provides WebGL 2; absence/failure switches to the full CPU compositor. No physical GPU is required for the default experience.

Phone, tablet and desktop retain film identity and every interaction feature, with modality-specific native touch arbitration. Initial viewport selects a stable 960 or 1600 delivery tier; backing/sample resolutions are bounded. This is feature parity, not a claim of identical pixel density, cropping or physical-device timing. Portrait contains the entire source film, with horizontally averaged dark boundary-color extensions, rather than losing the subject to a desktop crop.

## B. Behavior evidence

| System | Executed evidence | Scope/limits |
| --- | --- | --- |
| Scroll Film Transport (FT-01–10) | Source validation; mapping/race/cache/failed-decode unit cases; forward/reverse/endpoints/freeze/rapid reversals; real corrupted delivery frame and manual retry | Desired, decoded, resident, presented and draw-submitted states distinguished. No autonomous frame clock. |
| Liquid Navigator (LN-01–08) | Timestamp velocity and rate-independent segment dosing; slow/fast geometry; connected/distant component invariants; complete expiration; browser core/trail checks; rendered pixel differences and captures | Numeric morphology is supplemented by actual images. Perceived merging/shear/material quality still needs human approval. |
| Press-and-Hold (PH-01–13) | Continuous countdown, contraction, residual re-press/retarget, offscreen delivered coordinates, coverage/readiness gates, repeat Robot→Tree→Robot, cancel/blur/capture loss | Chromium delivers negative offscreen coordinates; Firefox/WebKit automation clamps at the viewport edge. Physical capture behavior remains to qualify. |
| Background Transition (VC-01–05) | Matching pair held during async scrub; incoming 404 cannot reveal/commit; retry loads latest pair; organic growth/release/resume captures; continuous cycle recording | Coverage model, CPU path and optional shader share the boundary function. Pixel differences demonstrate rendering, not aesthetic approval. |
| Input Modality | Native CDP touch contact/re-press/pan/orientation; desktop fine pointer; semantic keyboard buttons/range and visible focus; reduced motion | WebKit tap is native, held-contact injection is synthetic. Emulation does not prove physical touch, trackpad or Safari/iOS behavior. |
| Lifecycle (LC-01–07) | Resize/progress preservation, explicit suspension/restoration, real native reload/history recovery, disposal/late decode close, optional context loss/failure, reduced motion | Actual BFCache admission, OS tab freezing and browser zoom remain qualification gaps. Lifecycle hooks exercise the application handlers, not all operating-system conditions. |

Source media remains immutable. Validation checks exactly 192 named 3840×2160 PNGs per film, required PNG chunk structure/bounds and all CRCs without pixel decoding, and records SHA-256 hashes. Preparation has decoded all source PNGs into one-to-one WebP delivery derivatives. CRC validation is not a proof against arbitrary malicious compressed streams.

## C. Commands and final results

Commands below ran from `frontend/` on 2026-10-11. Results are observations; the complete gate did **not** pass.

| Command | Observed result |
| --- | --- |
| `npm run film:typecheck && npm run film:test` | Final rerun passed; 38 tests in five files, 1.57 s for units in the pre-commit rerun. |
| `npm run film:assets` / `npm run film:prepare` / `npm run film:build` | Executed separately and in combined gates; 384 canonical frames validated, 768 derivatives prepared/reused, isolated build passed. |
| `npm run film:check` (then-current WebKit environment: `LIBGL_ALWAYS_SOFTWARE=true GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=0`) | Source/type/unit/assets/build passed; 98 development cases: **79 passed, 18 skipped, 1 failed** in 7.3 min. WebKit crashed in `page.goto` before the first transport case. The chained built step was not reached. Preserved `crash-diagnostic/nonthreaded-integrated-failure.{log,json}`. |
| `npm run film:browser:built` | Final rerun with default browser configuration: **12 passed** in 39.9 s, including server build/startup. `final-built.log`. This passing run does not resolve intermittent startup faults. |
| `npm run film:browser -- --project=firefox --grep 'FT decode failure' --repeat-each=12` | **12 passed** after the native cached-corruption retry fix, with actual refreshed requests asserted. `retry-diagnostic/fixed-repeats.log`. |
| `DEBUG=pw:browser npm run film:browser -- --project=webkit --project=webkit-touch` (then-current non-threaded llvmpipe configuration) | **25 passed, 7 skipped**; `crash-diagnostic/nonthreaded-dev.log`. The subsequent complete gate still crashed. |
| `DEBUG=pw:browser npm run film:browser:built -- --project=webkit-built --project=webkit-touch-built --repeat-each=5` (then-current non-threaded llvmpipe configuration) | **20 passed**; `crash-diagnostic/nonthreaded-built.log`. |
| `GALLIUM_DRIVER=softpipe LIBGL_ALWAYS_SOFTWARE=true DEBUG=pw:browser npm run film:browser:built -- --project=webkit-built --project=webkit-touch-built --repeat-each=5` | Current implementation: **20 passed** in 3.1 min; `crash-diagnostic/softpipe-current-built.log`. |
| `GALLIUM_DRIVER=softpipe LIBGL_ALWAYS_SOFTWARE=true DEBUG=pw:browser npm run film:browser -- --project=webkit --project=webkit-touch` | **22 passed, 7 skipped, 3 failed** in 8.3 min; `crash-diagnostic/softpipe-current-dev.log`. No observed native crash; functional/timing failures remain described below. |
| `npm run typecheck && npm run test:unit` | Production frontend regression passed: **216 tests in 19 files**; experiment excluded by the existing root test configuration. |
| `npm run film:record` | Final desktop and native-touch recording run completed; `final-record.log`. |

Browser coverage: GPU-disabled Chromium desktop (1200×800), phone (390×844, DPR 2), tablet (768×1024, DPR 2), Firefox desktop, and Linux WebKit desktop/phone. The complete post-fix matrix passed every Chromium and Firefox case. Eighteen intentional skips separate fine-pointer tests from touch tests and restrict CDP bandwidth shaping/native held-touch injection to Chromium; no missing media or failed assertion is skipped. Browser tests run with zero automatic retries. CI configuration was updated but remote CI was not executed.

### Diagnosed failures and qualification environment

A preceding 98-case matrix passed 79, intentionally skipped 18 and caught one Firefox manual-retry failure. A separate 12-context diagnostic reproduced cached corrupt decoding in every context: same-URL native images failed without a new network request while a fresh query URL loaded. Explicit retry now increments delivery identity only for failed keys; successful frame identities stay unchanged. A deterministic regression verifies this scope, and the existing browser retry case now requires an actual refreshed request before recovery. Lifecycle timing metrics are also reset before restoration, avoiding a cumulative soak-timing row. The post-fix full matrix and targeted repeat results are reported above.

Earlier integrated runs exposed WebKit history recovery and teardown errors. An instrumented native-history reproduction showed the outgoing pagehide handler could see and overwrite the destination entry. Inactive writes now stop, native restored scroll remains authoritative, and suspension aborts queued/pending frame work. Repeated readiness callbacks also unnecessarily restarted the same neighbor prefetch; a deterministic regression preserves it. Unminified teardown diagnostics traced a remaining access-control page error to `createImageBitmap(blob)`. Cancelable native image transfer followed by `createImageBitmap(image)` removes this internal blob-URL path, with unit cases for early transfer cancellation, late decode ownership and failure cleanup. These are separate diagnosed issues; the prefetch fix alone did not establish the cause of the blob error. Playwright 1.57 Firefox `page.reload()` discarded entry state and reported navigation type `navigate`; reload qualification uses actual browser `location.reload()`.

An additional intermittent WPE process crash occurred at page startup, including 2 failures in 40 repeated built desktop cases under the default Linux driver. Captured native traces show `free(): invalid pointer` / SIGABRT entering `swrast_dri.so`, and a separate SIGSEGV entering `libEGL_mesa.so.0` / `swrast_dri.so`; Mesa is 23.2.1-1ubuntu3.1~22.04.4. This locates observed native faults in the software graphics path without proving the exact underlying driver bug or attributing every historical crash to it. Text backtraces are retained in ignored `crash-diagnostic/`; large core binaries were removed.

Single-threaded llvmpipe initially passed 20 cases, but the next integrated attempt had two startup crashes and was stopped (10 passed, 3 completed skips, one interrupted, 82 not run). The reporter includes unrun/interrupted cases in its skipped tally; they are not successful checks. That attempt is preserved as `crash-diagnostic/single-thread-integrated-failure.{log,json}`. Non-threaded llvmpipe then passed 25 development cases and 20 repeated built cases, but the next complete 98-case gate again crashed at startup. Neither passing repeat established a reliable workaround.

The pinned headed GTK alternative launched, but its 32-case subset produced 14 passes, 7 skips and 11 failures. Independent geometry probes found requested 390×844 mobile emulation yielded roughly 312×675 CSS pixels with a visual-viewport offset of 168.8 px and shifted fixed content. Desktop pointer coordinates also acquired fractional error. Scale/compositing/automation variations did not correct these observations. This mode was rejected as qualification evidence; no application geometry special cases or assertion relaxations were introduced. These results do not establish a production Safari defect.

An initial softpipe built repeat produced 15 passes and five phone selection timeouts. Low callback rates exposed capped hold response time: analytically stable hold expansion and retargeting now use actual visible elapsed time, with a deterministic low-callback regression. The current softpipe built repeat passed all 20 cases. Its development subset still failed three cases: navigator energy was zero at the post-stroke assertion; the controlled visual case exceeded its 180 s limit during 4.5 virtual seconds of dissipation; and phone semantic-retry commit exceeded the existing 5 s polling limit. Controlled visual advancement awaits actual RAF callbacks for each simulated step, so very low software-rendering rates can extend short virtual intervals into minutes. The log does not establish the complete cause of each remaining assertion failure, and they are retained as unresolved qualification failures.

All temporary driver and headed-port changes were removed from both harnesses. No automatic retries, page-error filtering or weaker assertions are retained. A final default-environment built run passed all 12 cases, but there is no reliable complete WebKit qualification or passing `film:check`. The implementation is therefore **BLOCKED** at that gate; neither the lack of a physical GPU alone nor occasional passing runs justify claiming cross-device success.

## D. Rendered, network and performance evidence

All generated evidence is ignored under `frontend/test-results/scroll-film-navigator/`. It is intentionally absent from Git.

- `visual/{project}/01-initial.png` through `18-reduced-motion.png`: initial/midpoint/final/reverse, slow/fast navigator, residue/dissipation, growth/contraction/re-press, organic reveal, two cycles, portrait/tree/landscape/reduced motion. Touch projects capture applicable states plus `mobile-hold-growth.png`.
- `visual/{project}/measurements.json`: controlled field morphology and rendered pixel differences.
- `recordings/interaction.webm`, `timeline.json`: ordinary desktop input and temporal evidence.
- `recordings/mobile-interaction.webm`, `mobile-timeline.json`: native CDP touch, cancel/re-press, repeated stationary hold, held orientation change and native pan.
- `performance/{chromium,chromium-touch}/report.json`: ordinary measurements with hidden diagnostics, environment and resource samples.
- `performance/{project}/browser-trace.json`, `trace-summary.json`: separate screenshot/raster/compositor trace workload; instrumented work excluded from ordinary timings.
- `network/{chromium,chromium-touch,chromium-tablet-touch}/report.json`: cold-cache 1 Mbps downstream / 150 ms added latency, observed first-frame/switch readiness and transfer totals.
- `crash-diagnostic/nonthreaded-integrated-failure.json`: preserved complete 98-case post-fix matrix; `browser-summary.json`: latest 32-case softpipe development subset, not the full matrix. `source-validation.json` and `media/manifest.json` retain source hashes and derivative options.

An actual Local Codex advisory run through the OpenDesign MCP inspected the implementation and supplied captures using `review-animations` and `critique-theater`. Its artifact is retained at `design-review/review.md`. Seven findings were deliberately addressed: interpolated refraction normals, idempotent/resumable semantic selection, completed-hold versus loading feedback, 44 px coarse targets, safe-area handling, uniform sampled portrait gutters, and geometry-bounded countdown placement. Added semantic-selection and continuous-ratio checks passed across all six projects. The advisory artifact reflects the pre-fix snapshot; it is not approval of the final result and did not benchmark physical hardware.

Default compositor screenshots were inspected directly. Controlled slow/fast/residual/dissipated captures before and after transparent-span optimization were pixel-identical (zero changed RGB pixels in each full screenshot). This pixel identity applies only to that earlier optimization. Later interpolated normals and portrait gutter changes intentionally alter pixels. Current Chromium deformation changed 31,841 pixels (9.98%) of the controlled film clip. Fresh recording samples are decoded and inspected separately; sampled frames cannot prove the absence of every transient artifact. The supplied brief is the Triptych interaction reference; its current website and underlying renderer were not independently reverse engineered. The neutral refracting material is an implementation choice, not a claim of exact reference reproduction.

Fresh desktop video is 10.64 s / 1,096,553 bytes at 1200×800; native-touch video is 7.92 s / 300,259 bytes at a fixed 390×844 recording size. Timeline observations include native scrubbing, complete trail decay, early contraction/re-press, Tree commit then Robot commit under continuous hold, portrait semantic selection and reduced motion. Native-touch observations include residual re-press, two commits through a held orientation change and native pan cancellation. Decoded temporal samples were inspected for both reveals and the phone portrait growth. When the viewport becomes landscape, Playwright fits it into the fixed portrait video and pads the unused area gray; this is recording padding, not a blank application stage. Full landscape screenshots show the actual viewport. Human motion/fidelity approval remains pending.

### Observed software-rendering envelope (2026-10-11)

Host: Intel Core i7-12700H, four logical CPUs exposed, 10.38 GB RAM, Linux 6.8.0-138-generic, Node 24.16.0, Chromium 143.0.7499.4. Both Chromium configurations launch with `--disable-gpu --disable-software-rasterizer`. Default backend is Canvas 2D with CPU material compositing; no WebGL resource/timer measurements apply. Desktop starts at 1200×800 / DPR 1; phone at 390×844 / DPR 2. These are viewport emulations on this CPU, not phone hardware benchmarks.

All duration columns are milliseconds. Request latency applies only to successfully draw-submitted targets; zero samples are shown as a dash.

| Project | Workload | Independent RAF p50 / p95 / p99 | Tick JS p50 / p95 / p99 | CPU draw p95 | Target-to-draw p95 (samples) |
| --- | --- | --- | --- | --- | --- |
| chromium | slow-scroll | 16.7 / 16.8 / 33.3 | 0.2 / 7.2 / 8.3 | 0.2 | 39.4 (21) |
| chromium | fast-scroll | 16.7 / 16.7 / 16.8 | 0.2 / 6.9 / 9.2 | 0.2 | 49.6 (4) |
| chromium | reverse-scrubbing | 16.7 / 33.4 / 50.0 | 0.3 / 8.4 / 9.3 | 0.2 | 10.0 (21) |
| chromium | dense-navigator | 16.7 / 33.3 / 33.4 | 10.3 / 11.9 / 15.3 | 10.1 | — (0) |
| chromium | simultaneous-scroll-fluid | 16.7 / 33.4 / 50.1 | 10.1 / 15.7 / 25.1 | 9.5 | 59.8 (27) |
| chromium | rapid-film-switches | — | 3.2 / 9.2 / 38.9 | 7.5 | 6.0 (4) |
| chromium | cancel-repress | — | 2.9 / 5.3 / 5.3 | 2.1 | — (0) |
| chromium | resize | — | 2.7 / 3.5 / 3.5 | 3.1 | — (0) |
| chromium | soak-1800-raf-steps | 16.7 / 33.5 / 50.0 | 9.5 / 16.1 / 17.8 | 9.5 | 60.2 (214) |
| chromium | suspension-restoration | — | 8.1 / 8.1 / 8.1 | 5.9 | 7.2 (1) |
| chromium-touch | slow-scroll | 16.7 / 16.8 / 16.8 | 0.2 / 5.4 / 7.3 | 0.2 | 21.4 (31) |
| chromium-touch | fast-scroll | 16.7 / 16.7 / 16.8 | 0.2 / 5.1 / 6.0 | 0.2 | 26.4 (5) |
| chromium-touch | reverse-scrubbing | 16.7 / 16.8 / 16.8 | 0.2 / 5.6 / 6.4 | 0.2 | 7.2 (24) |
| chromium-touch | dense-navigator | 16.7 / 33.3 / 33.4 | 12.5 / 14.7 / 19.3 | 11.6 | — (0) |
| chromium-touch | simultaneous-scroll-fluid | 16.7 / 33.4 / 50.1 | 12.8 / 18.2 / 23.6 | 12.2 | 64.4 (19) |
| chromium-touch | rapid-film-switches | — | 9.0 / 18.3 / 31.2 | 15.2 | 8.3 (4) |
| chromium-touch | cancel-repress | — | 5.1 / 7.4 / 7.4 | 3.0 | — (0) |
| chromium-touch | resize | — | 2.7 / 7.2 / 7.2 | 3.9 | — (0) |
| chromium-touch | soak-1800-raf-steps | 16.7 / 33.4 / 33.4 | 12.4 / 16.6 / 18.3 | 10.9 | 61.2 (196) |
| chromium-touch | suspension-restoration | — | 10.0 / 10.0 / 10.0 | 7.4 | 12.0 (1) |

- `chromium`: soak 44.91 s; explicit compositor estimate 21.22 MiB; sampled JS heap 2.33–5.50 MiB. Cache 8, peak decode concurrency 2, 3475 hits / 3344 misses / 1275 evictions; 0 obsolete completions, **0 stale presentations**, 0 load failures. Soak long tasks 0; active-RAF gap flags 792. Resize p95 25.5 ms (4 events). Restoration wall interval 43 ms (includes automation/observation). Separate trace: 96 screenshots, 96 DrawFrame events, 359 raster events with 0.250 ms p95, 217 paint events. Sampled event-listener counts: 52, 572, 255, 255, 48, 229.
- `chromium-touch`: soak 42.60 s; explicit compositor estimate 16.17 MiB; sampled JS heap 2.41–5.96 MiB. Cache 8, peak decode concurrency 2, 4161 hits / 3338 misses / 1535 evictions; 0 obsolete completions, **0 stale presentations**, 0 load failures. Soak long tasks 0; active-RAF gap flags 743. Resize p95 27.3 ms (9 events). Restoration wall interval 44 ms (includes automation/observation). Separate trace: 102 screenshots, 101 DrawFrame events, 232 raster events with 0.491 ms p95, 288 paint events. Sampled event-listener counts: 41, 549, 236, 40, 443, 757.

No CompositeLayers duration is exposed by either trace. The resource samples demonstrate application bitmap/decode bounds, not absence of browser/native-memory leaks. Heap, DOM and event-listener counts fluctuate with source image transfers and garbage collection; the observations do not establish a constant listener count or a leak-free process. Mixed and dense workloads show callback intervals above 16.7 ms, so universal 60 Hz is not established. Earlier optimization comparisons are historical, sequential observations; these tables reflect the latest post-fix full matrix and include subsequent visual refinements.

### Cold-cache shaped connection

1 Mbps downstream, 150 ms added request latency, 0.5 Mbps upstream. Transfer totals include development HTML/modules and completed media requests across the entire scenario, rather than just startup. These observations prove the complete renderer remains functional at this rate; they do not model cellular radio jitter or billing.

| Project | First frame ready | Organic selection ready | Scenario completed bytes | Completed requests |
| --- | --- | --- | --- | --- |
| chromium | 4.53 s | 1.95 s | 0.765 MB | 19 |
| chromium-touch | 4.53 s | 0.94 s | 0.570 MB | 19 |
| chromium-tablet-touch | 4.63 s | 1.98 s | 0.765 MB | 19 |

### Measurement interpretation

Workloads include slow/fast/reverse scroll, dense navigator, simultaneous fluid/scrub, four film selections, five hold cancel/re-press loops, five viewport sizes, 1800 requested RAF steps of soak and suspension/restoration. RAF count is not a fixed soak duration; actual elapsed time is recorded. Mobile performance runs also exercise fine-pointer fluid at a phone viewport to stress the full compositor, while native touch semantics have separate cases.

In-app tick timing includes CPU field/material work. `drawSubmissionMs` includes CPU pixel rasterization and native Canvas calls on the default backend; native browser composition may complete later. Successful `targetToDrawMs` samples measure request-to-command submission only; rapidly superseded targets are not latency samples. Active application RAF gaps and independent workload callback intervals are reported separately. `frameDrops` counts active RAF gaps over 25 ms, not physical display drops. Long tasks use the browser's supported PerformanceObserver. Browser trace event durations are separate instrumented observations, not photon/display latency or total GPU memory measurements.

Explicit application budgets are eight decoded bitmaps, two in-flight decode slots, two projected film slots and a fixed field of at most 756 KiB. At 1600, decoded bitmap residency is ≤43.95 MiB plus a conservative ≤21.97 MiB transient pixel allowance for two temporary native sources and two converting bitmaps; at 960, ≤15.82 MiB plus ≤7.91 MiB. Temporary native sources are released on conversion. Browser decoded-image cache retention remains browser-owned, outside these application estimates. The CPU compositor separately reports estimated native surfaces/pixel arrays; browser-native backing, compositor copies, HTTP cache and process overhead are outside this estimate. Zero explicit WebGL textures does not imply zero browser graphics memory. CDP JS heap does not include all decoded/native surfaces.

Cold-tier totals are 14,899,684 bytes (960) and 38,665,568 bytes (1600). At 1 Mbps, whole-tier transfer alone takes about 119/309 seconds. On-demand loading makes this unnecessary for startup; a few seconds cannot guarantee immediate arbitrary cold scrubbing. Preserve features and improve compression/decode/rendering based on fidelity and measured cost, rather than assuming device type identifies bandwidth or accepting feature removal.

### Server-side work boundary

Source validation, resizing, encoding and derivative generation already happen in the reproducible build/preparation pipeline, before the browser. No client-side source conversion or 4K transfer is performed. Input-dependent field simulation/compositing remains local to retain immediate interaction without per-gesture server requests. Production content-versioned caching/delivery infrastructure is a separate integration concern; no backend/service/deployment changes are made within this independent experiment.

## E. Repository footprint

All implementation, source-media ignore rule, preparation scripts, configurations, tests and documentation are inside the new experiment directory. Only `frontend/package.json` (11 `film:*` scripts) and `.github/workflows/ci.yml` (experiment typecheck/unit step) are modified outside it. No dependencies or lockfile changes. No Java/JTE/production frontend/Experiment 01 changes. The user subsequently authorized committing the implementation, tests and documentation; canonical sources and generated evidence remain excluded. No push is authorized or performed.

Canonical PNGs stay local under ignored `assets/frames/` (~1.5 GB). Reproducible WebP derivatives (768 files, 53.57 MB), builds, manifests, captures, videos and profiling data remain ignored. CI runs asset-independent typecheck/unit checks; ordinary checkout lacks source media, so CI does not claim media/build/browser qualification. `film:check` is the explicit full local gate.

Added-file groups inside the experiment:

| Group | Files |
| --- | --- |
| Entry/config/docs | `.gitignore`, `index.html`, `README.md`, `QUALIFICATION.md`, `vite.config.ts`, `tsconfig.json`, `vitest.config.ts`, `playwright.config.ts`, `production.playwright.config.ts` |
| Runtime | `src/{main,transport,loader,input,field,hold,material,canvas-renderer,renderer,metrics}.ts`, `src/style.css` |
| Media/evidence scripts | `scripts/{assets,prepare,record}.mjs` |
| Deterministic tests | `tests/{assets,transport,loader,field,hold}.test.ts` |
| Browser tests/helpers | `tests/{acceptance,resilience,visual,performance,network,built}.spec.ts`, `tests/{support,pixels}.ts` |

## F. Qualification limits

Human review must assess motion continuity, connected/separating liquid character, contour/refraction fidelity, subject framing and sample resolution across continuously varying ratios. Representative physical phone/tablet/desktop CPUs and browsers, real touch/trackpad, browser zoom, physical display latency, actual BFCache admission and varied radio/data-plan conditions still need qualification. Optional hardware WebGL can be profiled separately; the default renderer must remain GPU independent. Integration into production needs a separate authorized SSR/CSP/public-content task.

**Verdict: `BLOCKED`.** The implementation, source validation, deterministic tests, latest built matrix and Chromium/Firefox development cases are complete, with inspected browser captures and temporal recordings. The required complete development gate remains unsuccessful because of intermittent native WebKit startup faults; the tested slower alternative also has unresolved assertions/timeouts. No browser failures are waived. Requalify the unchanged complete suite in a reliable WebKit environment and diagnose any remaining reproducible behavior before calling the experiment functionally complete. Human visual approval and physical-device performance remain separate requirements. No production-readiness claim is made.

# Experiment 02 — sustained-scroll delivery continuation

2026-10-12, Europe/Istanbul. **READY_FOR_HUMAN_REVIEW**, with the measured limits below. This qualifies the focused continuation, not smooth playback, physical presentation or production integration. [STABILIZATION.md](STABILIZATION.md) remains the historical NEEDS_REWORK checkpoint.

## A. Starting state

Started clean on `feat/scroll-film-navigator`, HEAD `668f80eefe20bb876ffa1f745a62538431223f89`, the checkpoint committed at the user's explicit instruction. Its 79-file inventory matched stabilization. Starting runtime/tooling fingerprint: `2a96a6e0c5bb9df6bb5531c3e0b0806f61792f20920de30ca64ba0ec9cc561cf`.

Unresolved: approximately 1.25–2.05s captured identity holds without a causal trace. Prior 72 experiment units, 216 frontend units, 157 browser passes and 65 intentional skips remain historical evidence; they were not rerun. The accepted inconclusive desktop Linux WebKit exception remains unchanged.

Media publication remains `1cc6d4a6616442c9557b9b434630f0b86ef76b2aeac91c86ccb6ff3502f79a0d`. Cheap metadata/configuration/inventory/size consumption checks passed. No media regeneration or full hash/decode campaign occurred; retained integrity provenance remains in STABILIZATION.md/FOLLOWUP.md.

Final runtime/tooling fingerprint: `acf3a2a0439eea7b13c9bf5fda02f2508215d169d07bc97e62193251c8e53eac`, shared by all six final trajectories and the matching build. The [83-file freeze](../../test-results/scroll-film-navigator/delivery-continuation/source-freeze.json) includes the previous inventory plus two scripts and two unit files, excluding documentation/media. Changes remain uncommitted; no push or production integration occurred.

## B. Source-level diagnosis

| Finding | Evidence/consequence | Confidence |
| --- | --- | --- |
| `FrameCache.request/pump`, `src/transport.ts`: speculative 101/102 occupied both slots while current 103 waited | D1 deferred-loader reproduction passed against the original policy. Ordinary traversal did not trigger urgent preemption; queue priority could not create capacity. The old test protected this trade-off. | Confirmed policy weakness, not proof of all historical holds |
| `Renderer.info`, `src/renderer.ts`, called twice by development `state()` | Each inspection synchronously queried static GPU metadata. In eight seconds, 298 info reads consumed **4038.2ms**; metric summaries consumed 67.3ms; full-state reads consumed 4130.2ms. | Confirmed application-controlled diagnostic hotspot in this environment |
| Legacy RAF input plus immediate full-state read after `scrollTo` | The native scroll event had not yet updated desired state. New input/old desired state were repeatedly associated; expensive reads changed input/render/media scheduling. | Confirmed ordering and observer interference |
| First reservation repair with slow non-abortable work | Two jobs admitted as current became old. Freed slots repeatedly admitted another late current target, displacing predictions. At 230ms/24 targets per second: 3 ready, 67 consecutive unavailable, 30 canceled/obsolete operations. Reproduced with settlement-triggered re-requests. | Confirmed first-repair weakness; corrected by preserving admission roles |

Caching static backend identity alone reduced info-read time to **1.2ms**, full-state reads to **82.2ms**, and maximum moving submitted mismatch from **1268.1ms to 315.6ms**, before transport changes. Captured mismatch fell from 1294.1ms to 359.6ms. This isolates real observer-induced delivery delay, not merely JPEG undersampling. The exact native-driver mechanism behind costly queries remains unknown.

The original scheduler defect was demonstrated independently, but corrected-sampling baseline B/C rarely encountered it: only 1–2 selections per trajectory were blocked behind two originally speculative operations. Attributing all historical seconds-long holds to prefetch is unjustified.

## C. Measurement audit

Earlier load durations, draw-call times and captured identities remain valid observations of their workloads. Successful load duration excludes queue wait/censored targets and combines image availability with bitmap processing. Draw submission is not GPU completion or scanout. The old maximum hold is historical capture evidence, not representative of minimally observed native traversal in these controls.

New `?delivery=1` development instrumentation uses an 8192-record ring: actual selected generations, cache availability, queue/admission roles, image/bitmap wall-clock stages, cancellation/settlement, changed uploads, RAF and submitted identities. Final snapshots follow measurement; there are no active per-frame disk writes or full-state reads in B/C. Pure network, decode CPU and physical presentation are not measured.

Normal input uses a 16ms timer and eight-second wall-clock native-scroll trajectory. Browser blocking can delay that timer; actual input intervals are retained. RAF remains the application's rendering opportunity. Legacy probes intentionally retain RAF input and immediate inspection. No test driver becomes an application playback clock.

CDP JPEGs are promptly acknowledged and kept in bounded RAM; extraction and three example JPEG writes follow the workload. Epoch timestamps are converted through `performance.timeOrigin`; recorded clock offsets were −1 to 0ms. Captured mismatch is integrated against actual selected generations during moving input. Stationary setup/endpoints are excluded; uncaptured intermediate presentation is not declared absent. Capture cadence is not refresh cadence.

One initial C trajectory failed postprocessing because FFmpeg did not support the selected option. It is excluded, with its failure log retained. Only postprocessing was corrected to supported `-vsync 0`; C was repeated. Later scripts save the trace before extraction. The first A/B files lack per-run fingerprint receipts because the initial harness failed before its final receipt; the comparison discloses this limitation. Later controls and all final trajectories carry fingerprints.

## D. Controlled comparisons

Chromium **143.0.7499.4**, Playwright 1.57.0, VMware, explicit ANGLE Vulkan **SwiftShader**, WebGL2, 1200×800/DPR 1, full motion, robotic-arm, 1600px publication. The same identity shader was enabled with `capture=1` in both captured and uncaptured probes. A used a nine-frame prepared target window, 0–8, with normal bounded neighboring preparation. B/C/legacy used 0–191, fresh contexts and four seconds of HTTP/image warming, retaining only the bounded decoded cache. Forward/reverse share each case's warmed context. This is warm delivery, not cold-network qualification.

`N/H`: selected generations/cache hits. `A/R/D`: never admitted or available / never ready / never drawn before supersession. Latency distributions count successes only, alongside censored counts. Submitted mismatch measures continuous selected-versus-submitted identity difference during moving input.

| Probe | N/H | A/R/D | Max admission ms | Max submitted mismatch ms | Max captured mismatch ms | RAF p95 ms |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| A ready, original policy | 157/157 | 0/0/0 | 0 | 62.4 | — | 74.9 |
| B original forward | 119/116 | 0/0/3 | 0.1 | 140.1 | — | 111.5 |
| B original reverse | 134/132 | 0/0/2 | 0.2 | 79.1 | — | 94.7 |
| C original forward | 126/124 | 0/0/2 | 20.3 | 117.0 | 151.2 | 101.1 |
| C original reverse | 113/109 | 0/1/4 | 0.3 | 194.8 | 191.7 | 110.1 |
| Legacy original forward | 134/91 | 0/31/42 | 77.5 | 1139.8 | 1186.4 | 77.8 |
| Legacy original reverse | 121/69 | 0/28/52 | 72.6 | 661.5 | 698.0 | 85.9 |
| B final forward | 157/139 | 0/1/13 | 0.6 | 202.9 | — | 75.2 |
| B final reverse | 112/72 | 0/4/35 | 51.1 | 290.9 | — | 116.9 |
| C final forward | 112/80 | 0/0/29 | 0.1 | 189.6 | 261.8 | 109.7 |
| C final reverse | 110/76 | 0/3/30 | 0.1 | 258.2 | 295.9 | 113.5 |
| Legacy final forward | 118/68 | 0/5/45 | 14.1 | 273.4 | 270.1 | 94.5 |
| Legacy final reverse | 124/73 | 0/1/47 | 37.7 | 258.5 | 260.9 | 93.2 |

[Comparison JSON](../../test-results/scroll-film-navigator/delivery-continuation/comparison.json) contains all 19 valid trajectories: successful/censored target timing, queue wait, image/bitmap stages, cancellation settlement, work counts, inspection costs and capture cadence. Each run retains raw generation, upload and draw records.

A ruled out seconds-long ready-media submission holds in this probe. Corrected B/C did not reproduce historical seconds-long holds even before repair. Capture alone changed cadence/sampling but did not demonstrate a consistent seconds-scale penalty. Legacy sampling reproduced approximately 1.14–1.29s holds; GPU inspection caching substantially reduced them. D/Canvas was unnecessary and was not run.

Final B/C completed 163–186 admitted loads per trajectory, with 2–18 cancellations and zero completed obsolete bitmaps in those browser runs. Slot p95: 76.6–119.2ms; image-availability p95: 61.9–96.1ms; bitmap-operation p95: 23.8–46.7ms. These are wall-clock stages, not CPU measurements or throughput guarantees. Reservation eliminated two-speculative monopolization but reduced prefetched hits and increased superseded readiness/draw misses in some runs. Baseline B/C was often faster: this is a fairness/throughput trade-off, not a universal speed improvement.

## E. Changes

- `src/renderer.ts`: cache static identity per instance; keep buffer dimensions live. Restored contexts create new instances.
- `src/transport.ts`: preserve current/speculative admission roles through actual settlement; admit at most concurrency−1 prediction-owned operations. Ordinary single-film traversal retains productive current-owned work and a prediction lane; the latest current target gets the next current-lane settlement. Reversals/jumps override that preference. Paired targets use film-ownership fairness. Canceled work retains actual ownership. Cache, concurrency, lookahead and lead constants are unchanged.
- `src/main.ts`, `src/loader.ts`, `src/metrics.ts`: optional bounded causal observations and inspection-cost spans; native image/bitmap loading semantics remain intact.
- New `scripts/delivery-diagnose.mjs`, `scripts/delivery-analysis.mjs`: bounded controls, clock alignment, generation correlation and explicit censorship.
- New `tests/transport-admission.test.ts`, `tests/delivery-analysis.test.ts`: admission, predictions, non-abortable ownership, reversal/jumps, paired fairness, capacity/cleanup and diagnostic calculations.
- `tests/transport.test.ts`: replace the two-speculative-owner assertion with productive-neighbor retention and actual current admission. Reverse fixture fills the second slot with current work; bounded settlement replaces a microtask-count assumption.
- `tests/presentation.spec.ts`: repeated diagnostics avoid static GPU queries while buffer dimensions remain live; labeled output preserves historical evidence. README/QUALIFICATION point here.

Native scroll authority, wheel granularity, both 192-frame films, publication, shaders/fluid material, turquoise navigator, charcoal robot, themes, hold/residue/reveal and one commit per physical press remain preserved. No playback clock, catch-up, interpolation, deliberate presentation skipping, renderer downgrade, dependency or production change occurred.

## F. Focused verification

Executed from `frontend/`:

```sh
npm run film:test -- experiments/scroll-film-navigator/tests/transport.test.ts experiments/scroll-film-navigator/tests/transport-admission.test.ts experiments/scroll-film-navigator/tests/loader.test.ts experiments/scroll-film-navigator/tests/delivery-analysis.test.ts
npm run film:typecheck
FILM_SOFTWARE_GPU=1 FILM_REPORT_LABEL=delivery-focused npm run film:browser:focused -- -g 'Native scroll presents ready|Delayed target retains|Repeated diagnostics avoid|Calibrated wheel and continuous|PH cancellation, residual|Incoming failure cannot|GPU failure/loss'
npm run film:build
FILM_SOFTWARE_GPU=1 FILM_REPORT_LABEL=delivery-built npm run film:browser:built -- --project=chromium-built -g 'Built media, semantic controls, hook stripping'
FILM_SOFTWARE_GPU=1 FILM_DELIVERY_LABEL=final-repair FILM_DELIVERY_CASES=plain,capture,legacy FILM_DELIVERY_DIRECTIONS=1,-1 node experiments/scroll-film-navigator/scripts/delivery-diagnose.mjs
```

Choose a fresh diagnostic label to reproduce without overwriting evidence. Add `ready` for A; conditional `basic` was not used.

**30 focused units passed in 357ms**, typecheck passed, **7 Chromium regressions passed in 37.3s**, build passed in 2.74s, **1 Chromium built regression passed in 5.1s**. Browser checks cover mapping/stopped clock, delayed readiness, wheel granularity, hold/single commit, incoming failure/retry, context loss/restoration and metadata inspection. Built navigation/media/restoration and hook stripping passed. Emitted JS contained no `__filmExperiment`, delivery ring class, marking hook or inspection-cost span. Inactive optional observation branches may remain; no production ring or network telemetry exists.

Logs: [delivery-continuation](../../test-results/scroll-film-navigator/delivery-continuation/). Browser JSON: `delivery-focused.json`, `delivery-built.json` in the parent results directory. T1–T6 directly verify latest admission, productive prefetch, real non-abortable capacity/late bitmap closure, reversals/jumps/stale rejection, pair fairness, retry, suspension and cleanup. An additional role test proves predictions survive while latest current work receives the next current-owned settlement.

| T7: mock delay, 96 targets at 24/s | Ready | Never admitted | Max consecutive unavailable | Admitted / canceled / obsolete |
| --- | ---: | ---: | ---: | --- |
| 110ms | 35 | 24 | 5 | 75 / 0 / 0 |
| 230ms | 15 | 61 | 17 | 38 / 18 / 18 |

Newly admitted exact-current targets wait ≤one mock service time plus 1ms fake-timer resolution; predictions occupy ≤one role; pending work stays ≤2. Stationary readiness takes ≤residual occupancy plus own service, two mock service times. Every bitmap closes exactly once after suspension/disposal. Relative to the first repair, the 230ms case keeps 38 admissions but improves ready targets from 3 to 15 and reduces obsolete work from 30 to 18. These are scheduling/capacity assertions, not a VM FPS SLA.

All valid traces: zero ring loss (maximum 7135/8192), capture overflow, invalid pixels, page/render errors or stale presentations; cache ≤20 and peak pending ≤2. All final trajectories and browser/build checks use unchanged application source. The last unit-only edit adds censored-count reporting; the freeze includes it without invalidating runtime evidence.

## G. Residual limitations

Demonstrated priority inversion and inspection cost are repaired. Final short probes did not reproduce seconds-long avoidable application stalls; they do not prove absence under every workload. Reservation sacrifices speculative throughput for bounded admission, exposed by B/C and T7.

Two 110ms operations theoretically prepare fewer than 19 images/s; at 230ms, fewer than 9/s, below approximately 24 requested targets/s. Even after repair, the slow control delivers only 15 currently useful targets. Obsolete current work still consumes capacity under overload, but predictions make progress and stopped-target readiness is bounded. No claim of delivering every traversed position is made.

Input/RAF opportunities and media wall times remain constrained. Final B/C ready-to-draw p95 ranges 44.5–89.8ms. Remaining subsecond mismatch combines readiness, rendering opportunities and capture sampling. GPU execution/scanout and exact driver contribution were not measured; the entire residual is not assigned to “VM GPU” by exclusion.

The exact old 2.05s capture has no retrospective per-stage explanation. Reproduced legacy cost and corrected controls explain why that maximum is unsuitable as a normal-runtime oracle, preserving historical validity. No cold-load or full cross-browser qualification is claimed. Accepted WebKit status and deferred physical/CI coverage remain unchanged; no hardware hunt or new hardware gate is required.

## H. Efficiency

Seven bounded invocations: **19 valid eight-second trajectories**, plus one excluded eight-second postprocessing failure; approximately 160s active measurement and 44s HTTP/image warming, plus bounded setup/settlement. Cost probes isolated GPU metadata from metric-summary work. Final B/C/legacy were repeated once after the slow-loader regression justified the second policy.

Repair hypotheses: **two transport policies**, plus **one independently evidenced metadata-cache correction**. No numeric prefetch tuning or resource/format increase. Approximately 28MiB of bounded traces/selected JPEGs; no replacement approved video.

Avoided full functional/built matrices, production frontend/full experiment units, media regeneration, soak, WebKit investigation, toolchain/design preflight, OpenDesign review and previous final recording. No remote tool/generation calls were needed. Broad prior results remain historical checkpoints.

## I. Verdict

**READY_FOR_HUMAN_REVIEW.** Deterministic evidence enforces admission, productive prediction, paired fairness and actual ownership. The isolated diagnostic hotspot is corrected; legacy moving holds fell from approximately 1.27s to 0.27s in final forward evidence. Final probes admit or already have every selected target, disclose superseded generations and retain bounded work with subsecond mismatches. Focused runtime/restoration/built checks pass. Human review should assess the reservation trade-off and measured cadence, rather than expect every source position or guaranteed physical refresh from VMware.
